# Spec 01 — Project Scaffold, Flyway Migrations & Database Foundation

---

## 1. Goal & Objectives

Establish the complete baseline infrastructure for the B2B SaaS Billing Engine:
1. Valid Maven build configuration (`pom.xml`) with Java 21 LTS and Spring Boot 3.3+.
2. Docker Compose configuration defining a dedicated PostgreSQL 16 container.
3. Flyway migration `V1__init_schema.sql` defining the complete multi-tenant database schema.
4. Flyway seed script `V2__seed_plans.sql` providing default subscription tiers (Free, Pro, Enterprise).
5. Spring Boot application configuration (`application.yml`) wiring database, JPA, Flyway, and connection pooling.

---

## 2. Requirements & Deliverables

### Deliverable 1: `pom.xml`
- Java 21, Spring Boot 3.3.4 (or latest stable 3.3.x).
- Dependencies:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-security`
  - `spring-boot-starter-validation`
  - `org.postgresql:postgresql`
  - `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql`
  - `org.projectlombok:lombok`
  - `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` (0.12.x)
  - `spring-boot-starter-test`
  - `org.testcontainers:postgresql` + `testcontainers:junit-jupiter`

### Deliverable 2: `docker-compose.yml`
- Service: `postgres` (image `postgres:16-alpine`, port `5432:5432`, volume `b2b_postgres_data`).
- Environment: Database `billing_engine`, user `postgres`, password `postgres`.

### Deliverable 3: Database Schema Migrations
- `V1__init_schema.sql`:
  - `plans` table (id, name, monthly_price, max_users, max_api_calls).
  - `tenants` table (id UUID, company_name, plan_id, status, created_at).
  - `users` table (id UUID, tenant_id UUID, email, password_hash, role, created_at, unique constraint on `tenant_id + email`).
  - `subscriptions` table (id UUID, tenant_id UUID, plan_id, status, period_start, period_end).
  - `invoices` table (id UUID, tenant_id UUID, amount, status, idempotency_key, created_at).
  - `audit_logs` table (id UUID, tenant_id UUID, actor_id UUID, action, details_json, created_at).
- `V2__seed_plans.sql`:
  - Seed Free Tier: $0/month, 5 users, 1,000 API calls.
  - Seed Pro Tier: $49.00/month, 25 users, 50,000 API calls.
  - Seed Enterprise Tier: $299.00/month, unlimited users (9999), 1,000,000 API calls.

---

## 3. Verification Criteria
1. `docker compose up -d` successfully spins up PostgreSQL.
2. Spring Boot application boots cleanly, connects to PostgreSQL, and Flyway applies `V1` and `V2` migrations without error.
3. Database tables and indexes are verified via `psql` or database query.
