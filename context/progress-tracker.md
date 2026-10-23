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
| **Unit 06** | Testcontainers Integration Suite & Production Docker | Integration tests on real PostgreSQL container, multi-stage Dockerfile | ⚪ Pending |

---

## Phase History & Activity Log

- **2026-10-23**: Initialized project repository context files in `C:\Project\b2b-saas-billing/`. Established operating rules, architecture, engineering standards, and specs.
- **2026-10-23**: **Unit 01 Completed**. Configured Maven build with Spring Boot 3.3.4 and Java 21 LTS, self-contained Maven Wrapper, HikariCP connection pool, Flyway migrations (`V1__init_schema.sql` multi-tenant tables and compound indexes, `V2__seed_plans.sql` subscription tiers), baseline Spring Security 6 configuration, and verified 100% passing test suite.
- **2026-10-23**: **Unit 02 Completed**. Implemented stateless JWT security architecture with JJWT 0.12.x, `TenantContext` ThreadLocal isolation wrapper with guaranteed finally block cleanup, RFC 7807 Problem Details entry points (`JwtAuthenticationEntryPoint`, `CustomAccessDeniedHandler`, and `GlobalExceptionHandler`), method security, and 100% passing test suite (11/11 tests across unit and integration tests).
- **2026-10-23**: **Unit 03 Completed**. Implemented domain models, repositories, DTO records, and REST endpoints for Plan catalog, Tenant registration, secure login, tenant profile (`/api/tenants/me`), and team member management (`/api/users`) with real-time seat quota enforcement, audit trail logging, cross-tenant data isolation, and 100% passing test suite (17/17 tests).
- **2026-10-23**: **Unit 04 Completed**. Implemented high-precision proration billing engine (`BigDecimal` scale 2, `RoundingMode.HALF_UP`), row-level pessimistic write locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on subscriptions to prevent concurrent race conditions, active user downgrade quota guardrail, immutable invoice emission, audit logging, idempotent upgrade support, and 100% passing test suite (31/31 tests across unit, integration, and multi-threaded concurrency suites).
- **2026-10-23**: **Unit 05 Completed**. Implemented Flyway migration `V3__usage_metering_and_idempotency.sql`, `ApiUsage` and `IdempotencyRecord` domain entities/repositories, `UsageMeteringService`, `QuotaEnforcementInterceptor` with rate-limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`) and `403 ERR_QUOTA_EXCEEDED` gating, `UsageController` (`/api/usage/summary`), `InvoiceController` (paginated invoice history, dual-verification, pay transitions, audit logging), and 100% passing test suite (37/37 tests).

---

## Open Decisions & Technical Notes

1. **Database:** PostgreSQL 16 Alpine running on port `5432` via Docker Compose.
2. **Multi-Tenancy:** Discriminator column pattern (`tenant_id` on all tenant-owned tables) with `ThreadLocal` context management.
3. **Financial Precision:** `BigDecimal` with scale 2 and `RoundingMode.HALF_UP`.
