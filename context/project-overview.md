# Project Overview — B2B SaaS Billing Engine

Enterprise Multi-Tenant Subscription, Invoicing & Quota Orchestration OS.

---

## 1. Product Definition & Vision

**B2B SaaS Billing Engine** is an enterprise-grade, multi-tenant billing and resource management platform designed for SaaS organizations. It solves the critical operational challenges of modern B2B platforms: strict tenant data isolation, tier-based feature gating, automated recurring billing, usage quota enforcement, and tamper-proof audit trails.

Most junior backend portfolios showcase basic CRUD apps that lack real enterprise concerns: concurrency race conditions during subscription upgrades, tenant data leakage, floating-point rounding errors in invoices, and idempotency failures. This platform directly implements and showcases these mission-critical patterns.

---

## 2. Core Business Problems Solved

1. **Strict Multi-Tenant Isolation:** Complete logical isolation ensuring Company A can never read, modify, or leak data belonging to Company B.
2. **Deterministic Financial Math:** Zero floating-point rounding errors. All monetary figures are calculated using exact integer cents or `BigDecimal(10, 2)`.
3. **Idempotent Billing & Webhooks:** Eliminates duplicate credit card charges or double invoice generation caused by network retries or duplicate API clicks via unique idempotency keys.
4. **Enforced Resource Quotas:** Real-time quota enforcement (e.g., Free Tier: max 5 team members, 1,000 API calls/month; Pro Tier: 50 team members, 50,000 API calls/month). Exceeding limits triggers immediate `403 Forbidden` responses with upgrade paths.
5. **Invoice Lifecycle State Machine:** Invoices transition strictly through `DRAFT -> ISSUED -> PAID` (or `VOID` / `OVERDUE`). Invalid transitions are strictly rejected.

---

## 3. Core User Personas & Flows

### Persona A: SaaS Platform Super Admin
- Creates and configures global subscription plans (Free, Pro, Enterprise) with resource quotas and monthly/annual prices.
- Views tenant health metrics, system-wide active subscriptions, and aggregate monthly recurring revenue (MRR).

### Persona B: Tenant Administrator (Company Owner)
- Registers organization (tenant) and default admin account.
- Selects and upgrades/downgrades subscription tiers.
- Invites and manages team member seats up to the purchased quota.
- Views and downloads itemized billing invoices.

### Persona C: Tenant Team Member
- Authenticates via JWT scoped to their specific organization.
- Operates within assigned role permissions (`ROLE_ADMIN`, `ROLE_MEMBER`, `ROLE_BILLING`).

---

## 4. Key Feature Scope

| Feature Module | Scope Description |
| :--- | :--- |
| **Authentication & IAM** | Multi-tenant JWT auth, tenant registration, BCrypt password hashing, RBAC. |
| **Tenant Context Isolation** | Automatic `ThreadLocal` tenant resolution per request; query filtering via JPA. |
| **Subscription & Plans** | Plan definitions, quota tiering, prorated upgrades, downgrade grace periods. |
| **Invoicing & Billing** | Immutable invoice generation, PDF receipt metadata, idempotency key verification. |
| **Quota Enforcement** | Real-time seat allocation checks and rate-limiting middleware. |
| **Audit Log Trail** | Immutable log recording plan changes, user invitations, and invoice issuances. |

---

## 5. Success Criteria & Portfolio Impact

- **Zero Data Leakage:** 100% of integration test suites verify that cross-tenant queries return `404 Not Found` or `403 Forbidden`.
- **Concurrency Resilience:** Under 100 concurrent checkout attempts on a single-seat quota, exactly 1 succeeds and 99 fail with `409 Conflict` or `403 Quota Exceeded`.
- **Automated Verification:** 100% passing automated test suite with Testcontainers (real PostgreSQL) in CI/CD.
- **Turnkey Deployment:** Can be spun up locally with `docker compose up -d` in under 60 seconds.
