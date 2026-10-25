# Progress Tracker — B2B SaaS Billing Engine

Current project state, implementation roadmap, and completion status.

---

## Roadmap & Units Overview

| Unit | Title | Target Scope | Status |
| :--- | :--- | :--- | :--- |
| **Unit 01** | Project Scaffold & Database Foundation | Maven pom.xml, Docker Compose, PostgreSQL schema, Flyway migrations | ✅ Complete |
| **Unit 02** | Multi-Tenant JWT Security & Context Filter | Spring Security 6, JWT generation/validation, TenantContext ThreadLocal | ✅ Complete |
| **Unit 03** | Plans, Tenant Registration & Organization IAM | Plan seed data, tenant signup, user invite API with role checks | ✅ Complete |
| **Unit 04** | Subscription Upgrades, Proration & Concurrency | Tier upgrades, proration billing math, row-level pessimistic locks | ✅ Complete |
| **Unit 05** | Quota Enforcement Middleware & Idempotent Invoicing | Rate-limiting, resource limit gates (403), immutable invoice generation | ✅ Complete |
| **Unit 06** | Testcontainers Integration Suite & Production Docker | Integration tests on real PostgreSQL container, multi-stage Dockerfile | ✅ Complete |
| **Unit 07** | IAM & Multi-Tenant Isolation Remediation | Email collision fix, global uniqueness, top-level ThreadLocal cleanup, Flyway V4 | ✅ Complete |
| **Unit 08** | Idempotency Pipeline & Financial Ledger Hardening | Complete IdempotencyFilter, compound key constraint, payload hash check, RESTRICT cascade | ✅ Complete |
| **Unit 09** | Concurrency Control & Real-Time Quota Guardrails | Pessimistic locking on user seat invites, lock re-ordering, atomic API quota SQL, pay lock | ✅ Complete |
| **Unit 10** | Billing Precision & Subscription Lifecycle Remediation | Eliminate double primitives, expired renewal fix ($0 exploit), calendar-month cycles | ⚪ Pending |
| **Unit 11** | Security Hardening, CI Parity & Documentation Sync | Fail-fast JWT secret, invoice payment RBAC, CORS configuration, Testcontainers CI gate | ⚪ Pending |

---

## Phase History & Activity Log

- **2026-10-23**: Initialized project repository context files in `C:\Project\b2b-saas-billing/`. Established operating rules, architecture, engineering standards, and specs.
- **2026-10-23**: **Unit 01 Completed**. Configured Maven build with Spring Boot 3.3.4 and Java 21 LTS, self-contained Maven Wrapper, HikariCP connection pool, Flyway migrations (`V1__init_schema.sql` multi-tenant tables and compound indexes, `V2__seed_plans.sql` subscription tiers), baseline Spring Security 6 configuration, and verified 100% passing test suite.
- **2026-10-23**: **Unit 02 Completed**. Implemented stateless JWT security architecture with JJWT 0.12.x, `TenantContext` ThreadLocal isolation wrapper with guaranteed finally block cleanup, RFC 7807 Problem Details entry points (`JwtAuthenticationEntryPoint`, `CustomAccessDeniedHandler`, and `GlobalExceptionHandler`), method security, and 100% passing test suite (11/11 tests across unit and integration tests).
- **2026-10-23**: **Unit 03 Completed**. Implemented domain models, repositories, DTO records, and REST endpoints for Plan catalog, Tenant registration, secure login, tenant profile (`/api/tenants/me`), and team member management (`/api/users`) with real-time seat quota enforcement, audit trail logging, cross-tenant data isolation, and 100% passing test suite (17/17 tests).
- **2026-10-23**: **Unit 04 Completed**. Implemented high-precision proration billing engine (`BigDecimal` scale 2, `RoundingMode.HALF_UP`), row-level pessimistic write locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on subscriptions to prevent concurrent race conditions, active user downgrade quota guardrail, immutable invoice emission, audit logging, idempotent upgrade support, and 100% passing test suite (31/31 tests across unit, integration, and multi-threaded concurrency suites).
- **2026-10-23**: **Unit 05 Completed**. Implemented Flyway migration `V3__usage_metering_and_idempotency.sql`, `ApiUsage` and `IdempotencyRecord` domain entities/repositories, `UsageMeteringService`, `QuotaEnforcementInterceptor` with rate-limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`) and `403 ERR_QUOTA_EXCEEDED` gating, `UsageController` (`/api/usage/summary`), `InvoiceController` (paginated invoice history, dual-verification, pay transitions, audit logging), and 100% passing test suite (37/37 tests).
- **2026-10-23**: **Unit 06 Completed**. Implemented `PostgresIntegrationTest` leveraging Testcontainers (`@Testcontainers(disabledWithoutDocker = true)`) for verifying Flyway migrations `V1`-`V3`, UUID v4 generation, Spring Boot Actuator `/actuator/health` probe, row-level pessimistic write locking on real PostgreSQL 16 Alpine container, created production multi-stage `Dockerfile` (Eclipse Adoptium Temurin 21 JDK builder + JRE runtime, non-root user `appuser:appgroup`, layered JAR extraction, healthcheck probe), created `.dockerignore`, added `spring-boot-starter-actuator` to `pom.xml`, and verified 100% passing test suite (40 total tests).
- **2026-10-25**: **Comprehensive Security, Concurrency, Billing & Database Audit Completed**. Delivered full [AUDIT_REPORT.md](file:///c:/Project/b2b-saas-billing/AUDIT_REPORT.md) uncovering 5 Critical and 8 High severity findings across tenant isolation, user quota race conditions, $0.00 upgrade exploits, dead idempotency entities, and test skipping. Formulated Remediation Roadmap with Units 07–11 and created active specification files in `context/specs/`.
- **2026-10-25**: **Unit 07 Completed**. Implemented `V4__fix_tenant_isolation_and_indexes.sql` migration (enforced global `uq_users_email` UNIQUE constraint, dropped redundant B-Tree index on `invoices.idempotency_key`, added index on `subscriptions.plan_id`), eliminated cross-tenant email collision / DoS by validating email uniqueness globally in `AuthService` and `UserService`, refactored `JwtAuthenticationFilter` to enforce `TenantContext.clear()` in top-level `finally` block across 100% of execution paths (including unauthenticated/anonymous requests), added regression integration tests for cross-tenant registration rejection and filter thread cleanliness, and verified 100% passing test suite (42/42 tests passing).
- **2026-10-25**: **Unit 08 Completed**. Implemented `V5__scope_invoice_idempotency_and_protect_ledgers.sql` migration (scoped invoice idempotency key to tenant via `uq_invoices_tenant_idempotency (tenant_id, idempotency_key)`, protected financial ledgers from cascade deletions with `ON DELETE RESTRICT` on `invoices` and `audit_logs`, added `request_hash VARCHAR(64)` column to `idempotency_records`), implemented `IdempotencyFilter`, `CachedBodyHttpServletRequest`, and `IdempotencyService` supporting the 4-phase lifecycle (`PROCESSING`, `COMPLETED`, `FAILED`, 24h expiration), SHA-256 payload fingerprint verification rejecting key reuse with altered payload (`409 ERR_IDEMPOTENCY_CONFLICT`), replaying cached responses for identical requests, and created `IdempotencyIntegrationTest` (46/46 tests passing).
- **2026-10-26**: **Unit 09 Completed**. Implemented row-level pessimistic write locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on `Tenant` in `UserService.createUser` ensuring zero seat over-allocation under concurrent invite bursts, re-ordered lock boundaries and idempotent replay in `SubscriptionService.upgradeSubscription`, added row-level pessimistic locking in `InvoiceService.payInvoice` via `findByIdAndTenantIdWithLock` preventing duplicate payment processing and redundant audit trails, guarded API usage metering against race conditions and duplicate initial rows in `UsageMeteringService`, and created multi-threaded `QuotaAndPaymentConcurrencyTest` (49/49 tests passing).

---

## Open Decisions & Technical Notes

1. **Database:** PostgreSQL 16 Alpine running on port `5432` via Docker Compose.
2. **Multi-Tenancy:** Discriminator column pattern (`tenant_id` on all tenant-owned tables) with `ThreadLocal` context management.
3. **Financial Precision:** `BigDecimal` with scale 2 and `RoundingMode.HALF_UP`.
4. **Remediation Units:** Units 07 to 11 established as the formal remediation roadmap.
