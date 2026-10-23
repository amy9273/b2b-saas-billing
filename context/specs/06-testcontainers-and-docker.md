# Spec 06 — Testcontainers Integration Suite & Production Docker

---

## 1. Goal & Objectives

Finalize productionization, container packaging, and production-parity testing:
1. **Production Dockerfile**: Hardened multi-stage build leveraging Eclipse Adoptium Temurin 21 JRE Alpine, unprivileged non-root user execution, layered Spring Boot JAR optimization, JVM container flags, and built-in health probes.
2. **Container Build Hygiene (`.dockerignore`)**: Exclude build artifacts, git history, and local environment files to minimize image context size and protect credentials.
3. **Real PostgreSQL Testcontainers Suite (`PostgresIntegrationTest`)**: Validate application lifecycle, Flyway migrations (`V1`, `V2`, `V3`), compound indexes, and multi-tenant operations against real PostgreSQL 16 Alpine container with `@Testcontainers(disabledWithoutDocker = true)`.
4. **Production Health & Readiness Probes**: Verify Actuator `/actuator/health` endpoint for Kubernetes and container orchestrators.

---

## 2. Requirements & Deliverables

### Deliverable 1: Production Multi-Stage `Dockerfile`
- **Builder Stage**: `eclipse-temurin:21-jdk-alpine`, builds application and extracts layered JAR via `java -Djarmode=layertools -jar target/*.jar extract`.
- **Runtime Stage**: `eclipse-temurin:21-jre-alpine`:
  - Create non-root user: `addgroup -S appgroup && adduser -S appuser -G appgroup`.
  - Copy layers: `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`.
  - Expose port `8080`.
  - Healthcheck: `HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1`.
  - User: `USER appuser:appgroup`.
  - Entrypoint: `java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom org.springframework.boot.loader.launch.JarLauncher`.

### Deliverable 2: `.dockerignore`
- Exclude `target/`, `.mvn/`, `src/test/`, `.git/`, `.github/`, `.idea/`, `*.md`, `*.log`.

### Deliverable 3: `PostgresIntegrationTest`
- Test class in `src/test/java/com/enterprise/billing/testcontainers/PostgresIntegrationTest.java`:
  - `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
  - `@Testcontainers(disabledWithoutDocker = true)`
  - Starts `PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")`.
  - Configures `@DynamicPropertySource` to point DataSource and Flyway to the container.
  - Verifies:
    - Real PostgreSQL 16 connection established.
    - Flyway executed `V1`, `V2`, and `V3` cleanly.
    - PostgreSQL-specific native features (`gen_random_uuid()`, compound indexes, `TIMESTAMPTZ`) succeed without translation errors.
    - Multi-tenant tenant registration, plan retrieval, and invoice generation succeed on real PostgreSQL.

---

## 3. Verification Criteria
1. `mvn clean package -DskipTests` produces clean runnable artifact.
2. `PostgresIntegrationTest` succeeds when Docker daemon is accessible, and skips gracefully when Docker is unavailable without failing the build.
3. `/actuator/health` returns `200 OK` with status `UP`.
4. All 37 existing tests + new container tests pass with 0 failures and 0 errors.
