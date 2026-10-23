# Progress Tracker — B2B SaaS Billing Engine

Current project state, implementation roadmap, and completion status.

---

## Roadmap & Units Overview

| Unit | Title | Target Scope | Status |
| :--- | :--- | :--- | :--- |
| **Unit 01** | Project Scaffold & Database Foundation | Maven pom.xml, Docker Compose, PostgreSQL schema, Flyway migrations | ✅ Complete |
| **Unit 02** | Multi-Tenant JWT Security & Context Filter | Spring Security 6, JWT generation/validation, TenantContext ThreadLocal | ✅ Complete |
| **Unit 03** | Plans, Tenant Registration & Organization IAM | Plan seed data, tenant signup, user invite API with role checks | ⏳ Ready to Start |
| **Unit 04** | Subscription Upgrades, Proration & Concurrency | Tier upgrades, proration billing math, row-level pessimistic locks | ⚪ Pending |
| **Unit 05** | Quota Enforcement Middleware & Idempotent Invoicing | Rate-limiting, resource limit gates (403), immutable invoice generation | ⚪ Pending |
| **Unit 06** | Testcontainers Integration Suite & Production Docker | Integration tests on real PostgreSQL container, multi-stage Dockerfile | ⚪ Pending |

---

## Phase History & Activity Log

- **2026-10-23**: Initialized project repository context files in `C:\Project\b2b-saas-billing/`. Established operating rules, architecture, engineering standards, and specs.
- **2026-10-23**: **Unit 01 Completed**. Configured Maven build with Spring Boot 3.3.4 and Java 21 LTS, self-contained Maven Wrapper, HikariCP connection pool, Flyway migrations (`V1__init_schema.sql` multi-tenant tables and compound indexes, `V2__seed_plans.sql` subscription tiers), baseline Spring Security 6 configuration, and verified 100% passing test suite.
- **2026-10-23**: **Unit 02 Completed**. Implemented stateless JWT security architecture with JJWT 0.12.x, `TenantContext` ThreadLocal isolation wrapper with guaranteed finally block cleanup, RFC 7807 Problem Details entry points (`JwtAuthenticationEntryPoint`, `CustomAccessDeniedHandler`, and `GlobalExceptionHandler`), method security, and 100% passing test suite (11/11 tests across unit and integration tests).

---

## Open Decisions & Technical Notes

1. **Database:** PostgreSQL 16 Alpine running on port `5432` via Docker Compose.
2. **Multi-Tenancy:** Discriminator column pattern (`tenant_id` on all tenant-owned tables) with `ThreadLocal` context management.
3. **Financial Precision:** `BigDecimal` with scale 2 and `RoundingMode.HALF_UP`.
