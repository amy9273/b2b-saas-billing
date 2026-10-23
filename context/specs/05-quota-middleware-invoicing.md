# Spec 05 — Quota Enforcement Middleware & Idempotent Invoicing

---

## 1. Goal & Objectives

Implement runtime API usage metering, quota enforcement middleware, idempotency storage, and itemized invoice management:
1. **API Usage Metering**: Track API requests per tenant in the active billing period against their subscription plan (`maxApiCalls`).
2. **Quota Enforcement Middleware**: Intercept metered API requests. If a tenant exceeds their plan's monthly API allowance, reject the request with RFC 7807 `403 Forbidden` (`ERR_QUOTA_EXCEEDED`). Include rate-limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`).
3. **Usage Inspection Endpoint**: Expose `GET /api/usage/summary` returning total calls consumed, remaining calls, quota percentage, and period boundaries.
4. **Idempotency Storage Engine**: Database-backed idempotency table preventing duplicate write operations with status (`PROCESSING`, `COMPLETED`, `FAILED`).
5. **Invoice Management API (`InvoiceController`)**:
   - `GET /api/invoices`: Paginated invoice history for the authenticated tenant.
   - `GET /api/invoices/{id}`: Tenant-scoped invoice retrieval with dual-verification (404 on cross-tenant probe).
   - `POST /api/invoices/{id}/pay`: Transition invoice status from `PENDING` to `PAID` with audit logging.

---

## 2. Requirements & Deliverables

### Deliverable 1: Database Migration (`V3__usage_metering_and_idempotency.sql`)
- Table `api_usage`:
  - `id` (UUID PK)
  - `tenant_id` (UUID FK -> `tenants(id) ON DELETE CASCADE`)
  - `period_start` (DATE)
  - `period_end` (DATE)
  - `call_count` (BIGINT NOT NULL DEFAULT 0)
  - `created_at`, `updated_at` (TIMESTAMPTZ)
  - Compound unique constraint on `(tenant_id, period_start, period_end)`
- Table `idempotency_records`:
  - `id` (UUID PK)
  - `tenant_id` (UUID FK -> `tenants(id) ON DELETE CASCADE`)
  - `idempotency_key` (VARCHAR(100) NOT NULL)
  - `endpoint` (VARCHAR(255) NOT NULL)
  - `response_status` (INT)
  - `response_body` (TEXT)
  - `status` (VARCHAR(20) NOT NULL DEFAULT 'PROCESSING')
  - `created_at`, `expires_at` (TIMESTAMPTZ)
  - Compound unique constraint on `(tenant_id, idempotency_key)`

### Deliverable 2: Domain Entities & Repositories
- `ApiUsage` JPA entity & `ApiUsageRepository`:
  - `Optional<ApiUsage> findByTenantIdAndPeriodStartAndPeriodEnd(UUID tenantId, LocalDate periodStart, LocalDate periodEnd)`
  - `@Modifying @Query("UPDATE ApiUsage a SET a.callCount = a.callCount + 1, a.updatedAt = CURRENT_TIMESTAMP WHERE a.id = :id") int incrementCallCount(@Param("id") UUID id)`
- `IdempotencyRecord` JPA entity & `IdempotencyRecordRepository`:
  - `Optional<IdempotencyRecord> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey)`

### Deliverable 3: Metering & Quota Middleware
- `UsageMeteringService`:
  - Retrieves active subscription for tenant and current billing period boundaries.
  - Gets or creates `ApiUsage` for current cycle.
  - Verifies whether `callCount < plan.maxApiCalls`.
  - Increments usage count atomically.
- `QuotaEnforcementFilter`:
  - Registered in filter pipeline after `JwtAuthenticationFilter`.
  - Filters metered routes (`/api/**`, excluding `/api/auth/**`, `/api/plans/**`, `/actuator/**`).
  - Sets rate limit headers:
    - `X-RateLimit-Limit`
    - `X-RateLimit-Remaining`
  - Throws `QuotaExceededException` (`403 Forbidden`, `ERR_QUOTA_EXCEEDED`) when limit reached.

### Deliverable 4: DTO Records
- `UsageSummaryResponse`: `tenantId`, `planName`, `callCount`, `maxApiCalls`, `remainingCalls`, `usagePercentage`, `billingPeriodStart`, `billingPeriodEnd`
- `InvoiceResponse`: `id`, `tenantId`, `amount`, `status`, `idempotencyKey`, `billingPeriodStart`, `billingPeriodEnd`, `createdAt`

### Deliverable 5: Controllers
- `UsageController` (`/api/usage`):
  - `GET /summary`: Returns current quota consumption metrics.
- `InvoiceController` (`/api/invoices`):
  - `GET /`: Paginated list of invoices for authenticated tenant (`Page<InvoiceResponse>`).
  - `GET /{id}`: Single invoice detail with dual-verification.
  - `POST /{id}/pay`: Transition invoice status to `PAID`.

---

## 3. Verification Criteria
1. When tenant reaches `plan.maxApiCalls`, requests to metered endpoints are blocked with `403 Forbidden` (`ERR_QUOTA_EXCEEDED`).
2. Response headers `X-RateLimit-Limit` and `X-RateLimit-Remaining` accurately reflect usage.
3. `GET /api/usage/summary` returns accurate consumption percentage and period dates.
4. `GET /api/invoices` returns only invoices belonging to caller's tenant.
5. Attempting to query another tenant's invoice returns `404 Not Found`.
6. Successful payment transitions invoice to `PAID` and emits `AuditLog`.
7. 100% test pass rate across unit and integration tests.
