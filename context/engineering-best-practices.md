# Engineering Best Practices — B2B SaaS Billing Engine

Master coding, architecture, financial accuracy, security, performance, and database standards.

---

## 1. Financial Accuracy & Monetary Computation

- **Never Use Floating Point Primitives:** `float` and `double` suffer from IEEE 754 binary floating-point rounding errors (e.g., `0.1 + 0.2 != 0.3`). In enterprise financial software, this is fatal.
- **BigDecimal Standards:** Always use `BigDecimal` initialized with string literals:
  ```java
  // CORRECT
  BigDecimal price = new BigDecimal("49.99");
  BigDecimal tax = price.multiply(new BigDecimal("0.12")).setScale(2, RoundingMode.HALF_UP);

  // STRICTLY FORBIDDEN
  double badPrice = 49.99;
  BigDecimal badInit = new BigDecimal(49.99); // loses precision immediately
  ```
- **Proration Calculations:** When a tenant upgrades mid-billing cycle, calculate the unused fraction of the prior plan and apply it as a credit toward the new plan using exact day-based fractional math:
  $$\text{Prorated Credit} = \text{Old Plan Price} \times \left( \frac{\text{Days Remaining}}{\text{Total Days in Period}} \right)$$

---

## 2. Concurrency & Race Condition Elimination

- **Pessimistic Row-Level Locking:** When validating and deducting limited resources (e.g., claiming the last available seat in a subscription plan), use pessimistic locking to prevent race conditions:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM Subscription s WHERE s.tenantId = :tenantId")
  Optional<Subscription> findByTenantIdWithLock(@Param("tenantId") UUID tenantId);
  ```
- **Transaction Boundaries:** Keep `@Transactional` methods tightly scoped. Do not perform HTTP calls (e.g., email notifications, external payment gateways) inside an active database transaction to avoid holding database pool connections open unnecessarily.

---

## 3. Idempotency Key Architecture

Network timeouts frequently cause clients to retry requests. Without idempotency, a user clicking "Upgrade" twice during high latency would be charged twice.

### Standard Implementation Flow:
1. Client sends header: `X-Idempotency-Key: <unique-uuid>`.
2. Interceptor checks Redis / DB: `SELECT * FROM idempotency_records WHERE key = :key AND tenant_id = :tenantId`.
3. If record exists:
   - Status `COMPLETED`: Return cached response body and HTTP status code immediately without re-executing.
   - Status `PROCESSING`: Return `409 Conflict` (Operation currently in progress).
4. If record does not exist:
   - Insert record with status `PROCESSING`.
   - Execute service logic.
   - Update record to `COMPLETED` with response payload and 24-hour expiration.

---

## 4. Multi-Tenant Security & Defense-In-Depth

- **Dual-Verification on Queries:** Never trust client-supplied tenant IDs in URL paths or request bodies. Always verify that URL parameters match the authenticated `TenantContext.getTenantId()`:
  ```java
  if (!user.getTenantId().equals(TenantContext.getTenantId())) {
      throw new ResourceNotFoundException("Resource not found");
  }
  ```
  *(Note: Return `404 Not Found` rather than `403 Forbidden` to prevent leaking the existence of other tenants' resources).*
- **Password Security:** Use BCrypt with a minimum cost factor of `12` or Argon2id for hashing user passwords. Never store or log plaintext passwords.
- **Audit Trails:** Every state mutation (user invite, role change, plan upgrade, cancellation) must generate an immutable `AuditLog` row capturing `actor_user_id`, `action`, `entity_type`, `entity_id`, `changes_json`, and `timestamp`.

---

## 5. Performance, Indexing & Connection Pool Standards

- **Compound Indexing on Scoped Tables:** Every query executed in a multi-tenant system filters by `tenant_id`. Standard single-column indexes on `id` are insufficient for list/search queries. Always create compound indexes:
  ```sql
  CREATE INDEX idx_users_tenant_created ON users(tenant_id, created_at DESC);
  CREATE INDEX idx_invoices_tenant_status ON invoices(tenant_id, status);
  ```
- **HikariCP Configuration:** Configure pool size conservatively to avoid exhausting PostgreSQL connections:
  - `maximum-pool-size: 10`
  - `minimum-idle: 5`
  - `idle-timeout: 300000` (5 minutes)
  - `connection-timeout: 20000` (20 seconds)

---

## 6. Testing Best Practices (The Triple-Tier Test Strategy)

1. **Unit Tests (Fast, In-Memory):** Test pure business logic (proration math, quota verification, domain models) using JUnit 5 and Mockito. Zero I/O, runs in < 2 seconds.
2. **Repository Slice Tests:** `@DataJpaTest` with Flyway migrations to test custom SQL queries, locks, and constraint violations.
3. **End-to-End Integration Tests:** `@SpringBootTest` with **Testcontainers** running a real PostgreSQL 16 Docker container to guarantee production parity.
