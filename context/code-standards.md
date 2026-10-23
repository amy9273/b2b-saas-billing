# Code Standards & Quality Gates — B2B SaaS Billing Engine

Engineering conventions, Java 21 idioms, Spring Boot 3 standards, and CI/CD gates.

---

## 1. General Java 21 Engineering Principles

- **Immutability First:** Use Java 21 `record` classes for all DTOs, query projections, and event payloads. Records provide immutability, thread-safety, and eliminate verbose boilerplate.
- **Constructor Injection Only:** Field injection (`@Autowired` on private fields) is strictly forbidden. Always use constructor injection (or Lombok `@RequiredArgsConstructor(onConstructor_ = {@Autowired})`).
- **Null Safety & Optional:** 
  - Never return `null` from a service method. Return `Optional<T>` for nullable single items, or empty collections (`Collections.emptyList()`) for collections.
  - Never call `.get()` directly on an `Optional`. Use `.orElseThrow(...)` or functional chains (`.map()`, `.ifPresent()`).
- **Clean Stream Pipelines:** Avoid side-effects inside `.forEach()` or `.map()`. Streams must remain pure transformations.
- **No Console Logging:** Never use `System.out.println()`. Use SLF4J with parameterized logging (`log.info("Processing billing for tenant [{}]", tenantId)`).

---

## 2. Layered Architecture & Responsibilities

```
src/main/java/com/enterprise/billing/
├── config/          # Security, WebMvc, Flyway, and Bean definitions
├── context/         # TenantContext (ThreadLocal wrapper) and RequestContext
├── domain/          # JPA Entities and Value Objects (Private to Service layer)
├── dto/             # Java 21 Records for API Requests and Responses
├── exception/       # Domain Exceptions and @RestControllerAdvice (RFC 7807)
├── repository/      # Spring Data JPA Repositories (Tenant-scoped queries)
├── service/         # Core business logic, proration math, and transactional bounds
└── web/             # REST Controllers, route mappings, @Valid input checks
```

### Layer Boundaries & Invariants:
1. **Controller Layer:**
   - Only responsible for HTTP protocol mapping, status codes, and input validation using `@Valid`.
   - Must NEVER accept or return JPA `@Entity` objects. Always accept `RequestRecord` and return `ResponseRecord`.
2. **Service Layer:**
   - Coordinates business rules, quota checks, and transaction boundaries.
   - All write operations must be annotated with `@Transactional`. Read-only operations use `@Transactional(readOnly = true)`.
3. **Repository Layer:**
   - All multi-tenant queries must accept `UUID tenantId` as a parameter or apply a global tenant filter.

---

## 3. Standardized Error Handling (RFC 7807 Problem Details)

All exceptions must be caught by a centralized `@RestControllerAdvice` and transformed into standard RFC 7807 Problem Details JSON:

```json
{
  "type": "https://api.billing.enterprise.com/errors/quota-exceeded",
  "title": "Resource Quota Exceeded",
  "status": 403,
  "detail": "Free plan limit of 5 users reached. Upgrade to Pro to add more seats.",
  "instance": "/api/v1/users",
  "timestamp": "2026-10-23T08:15:00Z",
  "errorCode": "ERR_QUOTA_EXCEEDED",
  "metadata": {
    "currentUsage": 5,
    "maxAllowed": 5,
    "plan": "FREE"
  }
}
```

### Domain Exception Hierarchy:
- `BillingException` (Abstract Base)
  - `ResourceNotFoundException` $\rightarrow$ Maps to HTTP 404
  - `QuotaExceededException` $\rightarrow$ Maps to HTTP 403
  - `IdempotencyConflictException` $\rightarrow$ Maps to HTTP 409
  - `InvalidStateTransitionException` $\rightarrow$ Maps to HTTP 422
  - `UnauthorizedTenantAccessException` $\rightarrow$ Maps to HTTP 404 / 403

---

## 4. Database Migrations (Flyway Discipline)

- **Immutability of Migrations:** Never edit an existing migration script that has been merged or applied. Always create a new versioned migration:
  `V<timestamp>__<short_description>.sql` or `V1__init_schema.sql`, `V2__add_quota_indexes.sql`.
- **Idempotent Migration Scripts:** Always use `IF NOT EXISTS` or explicit constraints:
  ```sql
  CREATE TABLE IF NOT EXISTS audit_logs (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      tenant_id UUID NOT NULL REFERENCES tenants(id),
      action VARCHAR(100) NOT NULL,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
  );
  ```

---

## 5. Automated CI/CD Quality Gates

Every pull request must pass the automated CI pipeline (`mvn verify`) with:
1. **Zero Compiler Warnings:** `javac` configured with `-Werror -Xlint:all`.
2. **Checkstyle / Spotless Check:** Code formatting strictly verified against Google Java Style.
3. **Unit & Slice Tests:** Fast unit tests verify 100% of domain and service calculation logic.
4. **Integration Test Suite:** Testcontainers spins up real PostgreSQL 16 to verify all Flyway migrations and database queries.
