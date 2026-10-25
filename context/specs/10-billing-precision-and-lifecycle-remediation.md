# Spec 10 — Billing Math Precision & Subscription Lifecycle Remediation

---

## 1. Goal & Objectives

Resolve confirmed billing calculation anomalies, eliminate IEEE 754 floating-point primitives, and enforce subscription lifecycle integrity:
1. **Eliminate All Floating-Point Primitives (`double`/`float`)**: Refactor `UsageMeteringService` and `UsageSummaryResponse` to strictly use `BigDecimal` arithmetic (scale 2, `RoundingMode.HALF_UP`), restoring 100% compliance with Invariant #3.
2. **Prevent Post-Period / Expired Free Upgrade Exploit ($0.00 Net Due)**:
   - Detect when an upgrade request occurs after `currentPeriodEnd` has passed.
   - Enforce cycle renewal: reset `currentPeriodStart` and `currentPeriodEnd`, charging the full monthly price of the new tier.
3. **Transition to Calendar-Month Billing Cycles**: Replace fixed 30-day offsets (`plus(30, DAYS)`) with true calendar-month cycles (`plusMonths(1)`), correctly handling February and leap years.
4. **Negative Net Due Credit Ledger**: Support carry-over credit balances when downgrading instead of forfeiting unused balance.

---

## 2. In-Depth Root Cause & Mathematical Discrepancy Analysis

### Root Cause 1: Post-Period $0.00 Enterprise Exploit
- In `ProrationService.java`:
  ```java
  long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(calcDate, endDate));
  ```
- If a customer's subscription ended yesterday (`calcDate > endDate`):
  - `daysRemaining = 0`
  - `unusedFraction = 0.00`
  - `proratedCredit = 0.00`
  - `newPlanCharge = 0.00`
  - `netDue = 0.00`
- In `SubscriptionService.upgradeSubscription`:
  - Plan is updated to `ENTERPRISE`.
  - An invoice for `$0.00` is generated and marked `PAID`.
  - `currentPeriodEnd` remains in the past and is never updated!
  - Because no renewal scheduler exists, the customer receives permanent Enterprise access without paying.

### Root Cause 2: Invariant Violation in `UsageMeteringService`
- In `UsageMeteringService.java:L53`:
  ```java
  double usagePercentage = maxApiCalls > 0 ? (double) Math.round(((double) callCount / maxApiCalls) * 10000.0) / 100.0 : 0.0;
  ```
- Uses Java `double` primitives and IEEE 754 division, directly violating the repository invariant.

---

## 3. Requirements & Technical Deliverables

### Deliverable 1: Refactor `UsageSummaryResponse` to `BigDecimal`
- Update `UsageSummaryResponse`:
  ```java
  public record UsageSummaryResponse(
          UUID tenantId,
          String planName,
          long callCount,
          int maxApiCalls,
          long remainingCalls,
          BigDecimal usagePercentage,
          LocalDate billingPeriodStart,
          LocalDate billingPeriodEnd
  ) {}
  ```
- In `UsageMeteringService`:
  ```java
  BigDecimal usagePercentage = maxApiCalls > 0
          ? BigDecimal.valueOf(callCount)
              .multiply(BigDecimal.valueOf(100))
              .divide(BigDecimal.valueOf(maxApiCalls), 2, RoundingMode.HALF_UP)
          : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  ```

### Deliverable 2: Expired Subscription Renewal Logic
- In `SubscriptionService.upgradeSubscription`:
  ```java
  Instant now = Instant.now();
  boolean isExpired = !now.isBefore(subscription.getCurrentPeriodEnd());

  if (isExpired) {
      // Expired cycle: full reset
      subscription.setCurrentPeriodStart(now);
      subscription.setCurrentPeriodEnd(now.atZone(ZoneOffset.UTC).plusMonths(1).toInstant());
      subscription.setPlan(targetPlan);
      // Net due is the full monthly price of target plan
      BigDecimal netDue = targetPlan.getMonthlyPrice().setScale(2, RoundingMode.HALF_UP);
      // Emit full invoice for netDue
  } else {
      // Active cycle: compute standard mid-cycle proration
  }
  ```

### Deliverable 3: Calendar-Month Lifecycle Engine
- Update period creation in `AuthService` and `SubscriptionService` to use `ZonedDateTime.now(ZoneOffset.UTC).plusMonths(1).toInstant()`.

---

## 4. Test & Verification Plan

1. **Expired Subscription Upgrade Test**:
   - Create subscription with `currentPeriodEnd` = 2 days in the past.
   - Execute upgrade to `ENTERPRISE` ($299.00).
   - Assert `netDue` is `$299.00` (NOT `$0.00`).
   - Assert `currentPeriodStart` and `currentPeriodEnd` are reset to a new 1-month window.
2. **Usage Summary Float Invariant Test**:
   - Verify `usagePercentage` is an exact `BigDecimal` with scale 2.
   - Verify 1 call out of 3 total allowed yields `33.33` without IEEE 754 binary float drift.
