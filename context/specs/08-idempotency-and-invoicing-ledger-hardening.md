# Spec 08 — Idempotency Architecture & Financial Ledger Hardening

---

## 1. Goal & Objectives

Implement the complete, production-grade idempotency pipeline and fix financial data integrity flaws:
1. **Activate `IdempotencyRecord` Pipeline**: Wire the dead entity `IdempotencyRecord` and its repository into an automated `IdempotencyFilter` or `@Idempotent` aspect supporting the 4-phase lifecycle (`PROCESSING`, `COMPLETED`, `FAILED`, 24h expiration).
2. **Fix Cross-Tenant Key Collision in Invoices**: Scope `invoices.idempotency_key` to `(tenant_id, idempotency_key)`.
3. **Payload Fingerprint Verification**: Hash request payloads (SHA-256) and reject key reuse with altered payloads (`409 Conflict`).
4. **Prevent Cascade Deletion of Financial Ledgers**: Remove `ON DELETE CASCADE` from `invoices` and `audit_logs` to maintain regulatory audit immutability.

---

## 2. In-Depth Root Cause & Failure Mode Analysis

### Root Cause 1: Dead Code & Disconnected Architecture
- In Unit 05, `IdempotencyRecord` and `V3` table `idempotency_records` were created, but no filter or interceptor was implemented.
- `SubscriptionService` checked `invoices.idempotency_key` instead of managing execution states.

### Root Cause 2: Global Key Scope Causes Cross-Tenant Squatting
- In `V1__init_schema.sql`, `idempotency_key VARCHAR(100) UNIQUE` is global.
- Tenant A using key `TXN-001` prevents Tenant B from using key `TXN-001`, throwing a PostgreSQL unique violation and breaking multi-tenant autonomy.

### Root Cause 3: Cascade Deletion Destroys Financial Records
- Foreign keys on `invoices` and `audit_logs` use `ON DELETE CASCADE`.
- If a tenant account is deleted or pruned, the company's historical payment records, tax invoices, and immutable audit logs are permanently expunged, violating financial record-keeping standards (e.g. SOX, GAAP).

---

## 3. Requirements & Technical Deliverables

### Deliverable 1: Database Migration (`V5__scope_invoice_idempotency_and_protect_ledgers.sql`)
```sql
-- Scope invoice idempotency key to tenant
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_idempotency_key_key;
ALTER TABLE invoices ADD CONSTRAINT uq_invoices_tenant_idempotency UNIQUE (tenant_id, idempotency_key);

-- Protect financial ledgers from cascade deletions
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_tenant_id_fkey;
ALTER TABLE invoices ADD CONSTRAINT fk_invoices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;

ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_tenant_id_fkey;
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
```

### Deliverable 2: `IdempotencyFilter` / Middleware Component
- Intercepts mutating endpoints (`POST`, `PUT`, `DELETE`).
- Extracts `X-Idempotency-Key` header and authenticated `tenantId`.
- Computes SHA-256 hash of request URI + HTTP method + request body.
- Queries `idempotencyRecordRepository.findByTenantIdAndIdempotencyKey(tenantId, key)`:
  - If status == `COMPLETED`:
    - If payload hash does not match stored hash: Return `409 Conflict` (`ERR_IDEMPOTENCY_CONFLICT`, "Idempotency key reused with different request payload").
    - If payload hash matches: Return cached `responseStatus` and `responseBody` immediately.
  - If status == `PROCESSING` and not expired: Return `409 Conflict` ("Operation in progress").
  - If not found or expired:
    - Atomically insert record with status `PROCESSING`, payload hash, and `expiresAt = now + 24h`.
    - Proceed with filter chain inside a `ContentCachingResponseWrapper`.
    - Upon successful response, update record to `COMPLETED` with status code and body.
    - If service throws exception, update record to `FAILED` or delete to allow client retry.

### Deliverable 3: Update `SubscriptionService`
- Remove ad-hoc manual check from `SubscriptionService.upgradeSubscription` and rely on the centralized `IdempotencyFilter`.

---

## 4. Test & Verification Plan

1. **Cross-Tenant Key Replay Test**:
   - Tenant A executes upgrade with key `COMMON-KEY`.
   - Tenant B executes upgrade with key `COMMON-KEY`.
   - Assert both succeed and generate separate invoices without database error.
2. **Payload Mutation Test**:
   - Call endpoint with key `KEY-1` and body `{"targetPlanName":"PRO"}`.
   - Call endpoint with key `KEY-1` and body `{"targetPlanName":"ENTERPRISE"}`.
   - Assert second call receives `409 Conflict`.
3. **Concurrent Identical Request Test**:
   - Fire 2 concurrent requests with the same key.
   - Assert exactly 1 executes logic, and both receive consistent responses.
