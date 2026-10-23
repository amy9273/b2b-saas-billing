# Spec 04 — Subscription Upgrades, Proration & Concurrency

---

## 1. Goal & Objectives

Implement high-precision financial billing and subscription lifecycle management:
1. Deterministic financial computation: Prorated billing calculations utilizing strict `BigDecimal` math (scale 2, `RoundingMode.HALF_UP`), eliminating floating-point inaccuracies.
2. Preview Proration API (`POST /api/subscriptions/preview-proration`) calculating unused credit and net charge without state mutation.
3. Concurrency resilience: Row-level pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the tenant subscription to prevent race conditions during concurrent upgrade requests.
4. Downgrade validation guardrail: Preventing downgrades if current active user count exceeds the target tier's user quota.
5. Automated Invoice generation: Emitting immutable `Invoice` records on plan transitions.
6. Real-time audit trail: Logging plan transition events.

---

## 2. Requirements & Deliverables

### Deliverable 1: `Invoice` JPA Entity & Repository
- `Invoice`: `id` (UUID), `tenant` (ManyToOne), `amount` (`BigDecimal(10, 2)`), `status` (`PAID`, `PENDING`, `FAILED`, `VOID`), `idempotencyKey`, `billingPeriodStart` (`LocalDate`), `billingPeriodEnd` (`LocalDate`), `createdAt` (`Instant`).
- `InvoiceRepository`:
  - `Page<Invoice> findAllByTenantId(UUID tenantId, Pageable pageable)`
  - `Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId)`

### Deliverable 2: Pessimistic Lock on `SubscriptionRepository`
- Method:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM Subscription s WHERE s.tenant.id = :tenantId")
  Optional<Subscription> findByTenantIdWithLock(@Param("tenantId") UUID tenantId);
  ```

### Deliverable 3: Proration Calculation Engine (`ProrationService`)
- Pure calculation helper:
  - Formula:
    $$\text{Days Remaining} = \max(0, \text{Days between Now and Period End})$$
    $$\text{Total Days} = \max(1, \text{Days between Period Start and Period End})$$
    $$\text{Unused Fraction} = \frac{\text{Days Remaining}}{\text{Total Days}}$$
    $$\text{Prorated Credit} = \text{Old Plan Price} \times \text{Unused Fraction}$$
    $$\text{New Plan Charge} = \text{New Plan Price} \times \text{Unused Fraction}$$
    $$\text{Net Due} = \max(0.00, \text{New Plan Charge} - \text{Prorated Credit})$$
  - Scale: All monetary outputs scaled to `2` using `RoundingMode.HALF_UP`.

### Deliverable 4: DTO Records
- `ProrationPreviewResponse`: `oldPlanName`, `newPlanName`, `daysRemaining`, `totalDays`, `oldPlanPrice`, `proratedCredit`, `newPlanPrice`, `newPlanCharge`, `netDue`
- `UpgradeSubscriptionRequest`: `targetPlanName` (`@NotBlank`), `idempotencyKey` (optional string)
- `SubscriptionResponse`: `id`, `tenantId`, `plan`, `status`, `currentPeriodStart`, `currentPeriodEnd`, `daysRemaining`

### Deliverable 5: `SubscriptionService` & `SubscriptionController`
- `GET /api/subscriptions/me`: Returns active subscription details and days remaining.
- `POST /api/subscriptions/preview-proration`: Returns exact breakdown without database mutation.
- `POST /api/subscriptions/upgrade`:
  - Validates caller has `ROLE_ADMIN`.
  - Acquires pessimistic lock on `Subscription`.
  - Verifies target plan exists and is different from current.
  - If downgrading, verifies `activeUsers <= targetPlan.maxUsers`.
  - Calculates net due via `ProrationService`.
  - Updates `Subscription.plan` and `Tenant.plan`.
  - Creates `Invoice` with calculated `netDue`.
  - Records `AuditLog`.
  - Returns updated `SubscriptionResponse`.

---

## 3. Verification Criteria
1. Proration math verified with zero floating-point rounding errors across various mid-cycle upgrade scenarios.
2. Downgrade guardrail tested: Attempting to downgrade a tenant with 8 users to FREE tier (max 5) is rejected with `403 ERR_QUOTA_EXCEEDED`.
3. Simultaneous concurrent upgrade requests on the same tenant execute sequentially without race conditions or corrupted period dates.
4. Upgrade flow emits corresponding `Invoice` record and audit log.
5. 100% passing automated test suite.
