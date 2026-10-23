# Spec 03 — Plans, Tenant Registration & Organization IAM

---

## 1. Goal & Objectives

Implement the complete organization onboarding, authentication, and team member management lifecycle:
1. Public plan catalog endpoint (`GET /api/plans`) exposing tiered features and quotas.
2. Organization registration endpoint (`POST /api/auth/register-tenant`) provisioning a Tenant, default `FREE` subscription, default `ROLE_ADMIN` user with BCrypt hashed credentials, initial audit log, and issuing an authenticated JWT.
3. Secure user login endpoint (`POST /api/auth/login`) verifying credentials, tenant active status, and issuing a scoped JWT.
4. Tenant profile endpoint (`GET /api/tenants/me`) returning current tenant details, active plan, and status.
5. Team member seat management endpoints:
   - `GET /api/users`: Scoped list of users belonging strictly to the caller's tenant.
   - `POST /api/users`: Admin-only user invitation validating plan seat quotas (e.g. max 5 users for Free tier; exceeding triggers `403 ERR_QUOTA_EXCEEDED`).
6. Comprehensive audit logging for mutations (tenant registered, user added).

---

## 2. Requirements & Deliverables

### Deliverable 1: JPA Entities & Enums
- Entities in `com.enterprise.billing.domain`:
  - `Plan`: `id`, `name`, `monthlyPrice` (`BigDecimal`), `maxUsers`, `maxApiCalls`, `createdAt`
  - `Tenant`: `id` (UUID), `companyName`, `plan` (ManyToOne), `status` (Enum: `ACTIVE`, `SUSPENDED`, `CANCELLED`), `createdAt`
  - `User`: `id` (UUID), `tenant` (ManyToOne), `email`, `passwordHash`, `role` (Enum: `ROLE_ADMIN`, `ROLE_MEMBER`, `ROLE_BILLING`), `createdAt`
  - `Subscription`: `id` (UUID), `tenant` (ManyToOne), `plan` (ManyToOne), `status` (Enum: `ACTIVE`, `PAST_DUE`, `CANCELLED`), `currentPeriodStart`, `currentPeriodEnd`, `createdAt`
  - `AuditLog`: `id` (UUID), `tenantId`, `actorId`, `action`, `entityType`, `entityId`, `changesJson`, `createdAt`

### Deliverable 2: Repositories
- Repositories in `com.enterprise.billing.repository`:
  - `PlanRepository`: `Optional<Plan> findByName(String name)`
  - `TenantRepository`: `Optional<Tenant> findById(UUID id)`, `boolean existsByCompanyName(String name)`
  - `UserRepository`:
    - `Optional<User> findByEmail(String email)`
    - `Optional<User> findByTenantIdAndEmail(UUID tenantId, String email)`
    - `long countByTenantId(UUID tenantId)`
    - `List<User> findAllByTenantId(UUID tenantId, Pageable pageable)`
  - `SubscriptionRepository`: `Optional<Subscription> findByTenantId(UUID tenantId)`
  - `AuditLogRepository`: `List<AuditLog> findAllByTenantId(UUID tenantId, Pageable pageable)`

### Deliverable 3: DTO Records
- DTO records in `com.enterprise.billing.dto`:
  - `PlanResponse`: `id`, `name`, `monthlyPrice`, `maxUsers`, `maxApiCalls`
  - `RegisterTenantRequest`: `companyName`, `adminEmail`, `password`, `planName`
  - `LoginRequest`: `email`, `password`
  - `AuthResponse`: `token`, `tokenType`, `userId`, `tenantId`, `email`, `role`, `companyName`, `planName`
  - `CreateUserRequest`: `email`, `password`, `role`
  - `UserResponse`: `id`, `tenantId`, `email`, `role`, `createdAt`
  - `TenantResponse`: `id`, `companyName`, `status`, `plan`, `createdAt`

### Deliverable 4: Service Layer
- Services in `com.enterprise.billing.service`:
  - `PlanService`: Retrieves all plans or by name.
  - `AuthService`:
    - `registerTenant`: Creates Tenant, Subscription, Admin User, AuditLog in `@Transactional` boundary, generates JWT.
    - `login`: Verifies user exists, validates BCrypt password, checks tenant status is `ACTIVE`, returns JWT.
  - `UserService`:
    - `getUsersByTenant`: Returns paginated list filtered by `TenantContext.getTenantId()`.
    - `createUser`: Checks `countByTenantId` against `plan.getMaxUsers()`. If current count >= limit, throws `QuotaExceededException`. If unique constraint violated, throws `DuplicateResourceException`. Otherwise saves user and logs audit.
  - `TenantService`:
    - `getCurrentTenant`: Fetches tenant using `TenantContext.getTenantId()`.
  - `AuditService`:
    - Records immutable audit actions.

### Deliverable 5: Controllers
- Controllers in `com.enterprise.billing.web`:
  - `PlanController`: `GET /api/plans` (Public)
  - `AuthController`: `POST /api/auth/register-tenant`, `POST /api/auth/login` (Public)
  - `TenantController`: `GET /api/tenants/me` (Authenticated)
  - `UserController`: `GET /api/users`, `POST /api/users` (Admin only)

---

## 3. Verification Criteria
1. Plan listing returns seeded Free, Pro, and Enterprise tiers.
2. Tenant registration succeeds, persists entities in PostgreSQL / H2, hashes password with BCrypt, and returns a valid JWT.
3. Login succeeds with correct credentials and rejects incorrect credentials with `401 Unauthorized`.
4. User creation increments seat count up to the plan limit (5 for Free tier).
5. Attempting to add a 6th user on the Free tier immediately returns `403 Forbidden` with RFC 7807 Problem Details (`ERR_QUOTA_EXCEEDED`).
6. Cross-tenant isolation test: Tenant A cannot view Tenant B's users.
7. Automated test suite runs with 100% pass rate.
