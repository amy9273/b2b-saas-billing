# Progress Tracker — B2B SaaS Billing Engine

Current project state, implementation roadmap, and completion status.

---

## Roadmap & Units Overview

| Unit | Title | Target Scope | Status |
| :--- | :--- | :--- | :--- |
| **Unit 01** | Project Scaffold & Database Foundation | Maven pom.xml, Docker Compose, PostgreSQL schema, Flyway migrations | ⏳ Ready to Start |
| **Unit 02** | Multi-Tenant JWT Security & Context Filter | Spring Security 6, JWT generation/validation, TenantContext ThreadLocal | ⚪ Pending |
| **Unit 03** | Plans, Tenant Registration & Organization IAM | Plan seed data, tenant signup, user invite API with role checks | ⚪ Pending |
| **Unit 04** | Subscription Upgrades, Proration & Concurrency | Tier upgrades, proration billing math, row-level pessimistic locks | ⚪ Pending |
| **Unit 05** | Quota Enforcement Middleware & Idempotent Invoicing | Rate-limiting, resource limit gates (403), immutable invoice generation | ⚪ Pending |
| **Unit 06** | Testcontainers Integration Suite & Production Docker | Integration tests on real PostgreSQL container, multi-stage Dockerfile | ⚪ Pending |

---

## Phase History & Activity Log

- **2026-10-23**: Initialized project repository context files in `C:\Project\b2b-saas-billing/`. Established operating rules, architecture, engineering standards, and specs.

---

## Open Decisions & Technical Notes

1. **Database:** PostgreSQL 16 Alpine running on port `5432` via Docker Compose.
2. **Multi-Tenancy:** Discriminator column pattern (`tenant_id` on all tenant-owned tables) with `ThreadLocal` context management.
3. **Financial Precision:** `BigDecimal` with scale 2 and `RoundingMode.HALF_UP`.
