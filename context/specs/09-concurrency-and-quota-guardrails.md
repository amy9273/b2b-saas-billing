# Spec 09 — Concurrency Control & Real-Time Resource Quota Guardrails

---

## 1. Goal & Objectives

Remediate concurrency race conditions across user invitations, invoice payments, and usage metering:
1. **Pessimistic Locking on Seat Allocations (`UserService.createUser`)**: Eliminate race conditions that allow tenants to invite more users than permitted by their subscription tier.
2. **Lock-Safe Idempotency in `SubscriptionService.upgradeSubscription`**: Eliminate race condition between idempotency check and pessimistic subscription lock.
3. **Atomic API Quota Metering (`UsageMeteringService`)**: Prevent concurrent initial request crashes (`uq_api_usage_tenant_period`) and race condition oversaturation on API limits.
4. **Optimistic/Pessimistic Locking on Invoice Payments (`InvoiceService.payInvoice`)**: Prevent duplicate payment processing and redundant audit log generation.

---

## 2. In-Depth Root Cause & Failure Mode Analysis

### Root Cause 1: Unlocked Read-Then-Write in User Creation
- **Sequence Diagram:**
  ```
  Thread 1 (Admin A)                       Thread 2 (Admin B)
         │                                        │
         ├─── countByTenantId (reads 4) ──────────┤
         │                                        ├─── countByTenantId (reads 4)
         ├─── 4 < 5 (Max Users) -> PASS           │
         │                                        ├─── 4 < 5 (Max Users) -> PASS
         ├─── INSERT User 5 (Commits)             │
         │                                        ├─── INSERT User 6 (Commits)
         ▼                                        ▼
  Result: Tenant now has 6 users on a 5-seat plan (Quota Over-Allocation).
  ```

### Root Cause 2: Unsafe Lock Ordering in `SubscriptionService`
- Step 2 checks invoice idempotency before acquiring the pessimistic lock in Step 3.
- Concurrent requests with identical keys both pass Step 2, then serialize on Step 3, causing the second thread to throw `IllegalArgumentException: Tenant is already subscribed to plan: PRO` instead of replaying the invoice.

### Root Cause 3: API Usage Metering Race Condition
- `findByTenantIdAndPeriodStartAndPeriodEnd` followed by `orElseGet(save)` causes duplicate key violation under concurrent initial calls.
- In-memory `usage.getCallCount() >= plan.getMaxApiCalls()` is evaluated before atomic SQL increment, allowing traffic bursts to exceed quota limits.

---

## 3. Requirements & Technical Deliverables

### Deliverable 1: Pessimistic Lock on `Tenant` in `UserService`
- In `TenantRepository`:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Tenant t WHERE t.id = :id")
  Optional<Tenant> findByIdWithLock(@Param("id") UUID id);
  ```
- In `UserService.createUser`:
  ```java
  Tenant tenant = tenantRepository.findByIdWithLock(tenantId)
          .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

  long currentUsers = userRepository.countByTenantId(tenantId);
  int maxAllowed = tenant.getPlan() != null ? tenant.getPlan().getMaxUsers() : 5;
  if (currentUsers >= maxAllowed) {
      throw new QuotaExceededException(String.format(
              "Plan limit of %d users reached (current: %d). Upgrade your subscription tier to invite more seats.",
              maxAllowed, currentUsers
      ));
  }
  ```

### Deliverable 2: Lock Re-ordering in `SubscriptionService`
- Acquire `subscriptionRepository.findByTenantIdWithLock(tenantId)` first.
- Evaluate idempotency and state transitions within the acquired transaction lock boundary.

### Deliverable 3: Atomic Usage Metering in PostgreSQL
- In `ApiUsageRepository`:
  ```java
  @Modifying
  @Query(value = """
      INSERT INTO api_usage (id, tenant_id, period_start, period_end, call_count, created_at, updated_at)
      VALUES (gen_random_uuid(), :tenantId, :start, :end, 1, :now, :now)
      ON CONFLICT (tenant_id, period_start, period_end)
      DO UPDATE SET call_count = api_usage.call_count + 1, updated_at = :now
      WHERE api_usage.call_count < :maxCalls
  """, nativeQuery = true)
  int recordUsageIfUnderQuota(@Param("tenantId") UUID tenantId, @Param("start") LocalDate start,
                              @Param("end") LocalDate end, @Param("maxCalls") int maxCalls,
                              @Param("now") Instant now);
  ```
- If returned rows == 0 and record exists, throw `QuotaExceededException`.

### Deliverable 4: Lock Protection on `InvoiceService.payInvoice`
- Add `@Version private Long version;` to `Invoice` entity, OR:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.tenant.id = :tenantId")
  Optional<Invoice> findByIdAndTenantIdWithLock(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
  ```

---

## 4. Test & Verification Plan

1. **User Seat Concurrency Test**:
   - Free plan tenant with 4 existing users.
   - 10 concurrent threads attempt `POST /api/users`.
   - Assert exactly 1 succeeds, 9 fail with 403 `ERR_QUOTA_EXCEEDED`, and final user count in DB is exactly 5.
2. **Concurrent Invoice Payment Test**:
   - 5 concurrent threads attempt `POST /api/invoices/{id}/pay` for the same invoice.
   - Assert exactly 1 succeeds, 4 fail with `400 Bad Request` or `OptimisticLockingFailureException`, and exactly 1 `INVOICE_PAID` audit log is created.
