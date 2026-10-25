# Spec 07 — IAM & Multi-Tenant Isolation Remediation

---

## 1. Goal & Objectives

Address critical tenant isolation vulnerabilities and authentication failure modes identified in the repository audit:
1. **Eliminate Cross-Tenant Email Collision & DoS**: Replace global email query in login with tenant-scoped authentication or enforce global email uniqueness to prevent `IncorrectResultSizeDataAccessException` (HTTP 500).
2. **Guarantee Filter ThreadLocal Cleanup on 100% of Paths**: Refactor `JwtAuthenticationFilter` so that `TenantContext.clear()` executes in a top-level `finally` block for all incoming requests (authenticated, unauthenticated, and error paths).
3. **Database Schema Alignment (Flyway V4)**:
   - Provide migration `V4__fix_tenant_isolation_and_indexes.sql`.
   - Resolve index redundancies (duplicate index on `invoices.idempotency_key`).
   - Add missing foreign key index on `subscriptions(plan_id)`.

---

## 2. In-Depth Root Cause & Failure Mode Analysis

### Root Cause 1: Unqualified Email Lookup on Scoped Unique Constraint
- **Current State:**
  - Database schema: `CONSTRAINT uq_tenant_user_email UNIQUE (tenant_id, email)`
  - Repository query: `Optional<User> findByEmail(String email)` generates `SELECT ... FROM users WHERE email = ?` without a `tenant_id` predicate.
- **Failure Scenario:**
  - Company A registers with admin `admin@startup.com`.
  - Company B registers with admin `admin@startup.com`. Both registrations succeed because `tenant_id` differs.
  - When either admin calls `POST /api/auth/login`, Spring Data JPA encounters 2 rows and throws `IncorrectResultSizeDataAccessException`.
  - Global exception handler maps this to `500 Internal Server Error`, permanently locking out both users.
- **Attack Vector:** An attacker can perform a targeted DoS against any tenant by registering with the victim's email address.

### Root Cause 2: Unauthenticated Path Bypasses Filter Cleanup
- **Current State:**
  ```java
  if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
  }
  ```
- **Failure Scenario:** When Tomcat reuses a pooled thread that previously retained an uncleared `TenantContext` (e.g. if an asynchronous task or uncaught error bypassed cleanup), public requests executing on that thread inherit the prior tenant's ID.

---

## 3. Requirements & Technical Deliverables

### Deliverable 1: Database Migration (`V4__fix_tenant_isolation_and_indexes.sql`)
- Enforce global email uniqueness:
  ```sql
  -- Drop composite constraint and enforce global uniqueness on email
  ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_tenant_user_email;
  ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);

  -- Drop duplicate redundant index on invoices.idempotency_key
  DROP INDEX IF EXISTS idx_invoices_idempotency;

  -- Add missing foreign key index on subscriptions(plan_id)
  CREATE INDEX IF NOT EXISTS idx_subscriptions_plan ON subscriptions(plan_id);
  ```

### Deliverable 2: Registration Validation & Scoped Lookup
- In `AuthService.registerTenant`:
  - Check `userRepository.existsByEmail(normalizedEmail)`.
  - Throw `DuplicateResourceException("Email is already registered: " + normalizedEmail)` if present.
- In `UserRepository`:
  - Keep `findByEmail(String email)` now backed by global `uq_users_email`.

### Deliverable 3: Top-Level `TenantContext.clear()` in Filter
- In `JwtAuthenticationFilter`:
  ```java
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
          throws ServletException, IOException {
      try {
          String authHeader = request.getHeader("Authorization");
          if (authHeader != null && authHeader.startsWith("Bearer ")) {
              // process token, set SecurityContext, set TenantContext
          }
          filterChain.doFilter(request, response);
      } finally {
          TenantContext.clear();
      }
  }
  ```

---

## 4. Test & Verification Plan

1. **Email Collision Integration Test**:
   - Register Tenant A with `admin@test.com`.
   - Attempt to register Tenant B with `admin@test.com`.
   - Assert `409 Conflict` (`ERR_DUPLICATE_RESOURCE`) is returned and Tenant A can still log in cleanly.
2. **Unauthenticated Thread Cleanliness Test**:
   - Manually inject a tenant ID into `TenantContext`.
   - Execute an unauthenticated request through `JwtAuthenticationFilter`.
   - Assert `TenantContext.getTenantId()` is null after execution.
