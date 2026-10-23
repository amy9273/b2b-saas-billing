package com.enterprise.billing.testcontainers;

import com.enterprise.billing.domain.Plan;
import com.enterprise.billing.domain.Subscription;
import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.domain.TenantStatus;
import com.enterprise.billing.repository.PlanRepository;
import com.enterprise.billing.repository.SubscriptionRepository;
import com.enterprise.billing.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billing_test_db")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @LocalServerPort
    private int port;

    @Autowired(required = false)
    private TestRestTemplate restTemplate;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private PlanRepository planRepository;

    @Autowired(required = false)
    private TenantRepository tenantRepository;

    @Autowired(required = false)
    private SubscriptionRepository subscriptionRepository;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Real PostgreSQL 16 container starts and Flyway migrations execute successfully")
    void testPostgreSQLConnectionAndFlywayMigrations() {
        assertThat(postgres.isRunning()).isTrue();

        // Verify Flyway migrations V1, V2, and V3 executed against real PostgreSQL
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class
        );
        assertThat(migrationCount).isGreaterThanOrEqualTo(3);

        // Verify PostgreSQL native gen_random_uuid() functioned cleanly
        UUID sampleUuid = jdbcTemplate.queryForObject("SELECT gen_random_uuid()", UUID.class);
        assertThat(sampleUuid).isNotNull();

        // Verify seeded subscription plans exist in real PostgreSQL
        assertThat(planRepository.findByName("FREE")).isPresent();
        assertThat(planRepository.findByName("PRO")).isPresent();
        assertThat(planRepository.findByName("ENTERPRISE")).isPresent();
    }

    @Test
    @DisplayName("Actuator health check endpoint returns 200 OK with UP status")
    void testActuatorHealthEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @Transactional
    @DisplayName("End-to-End Multi-Tenant Lifecycle on Real PostgreSQL 16 Container")
    void testMultiTenantLifecycleOnRealPostgres() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            Plan proPlan = planRepository.findByName("PRO").orElseThrow();

            Tenant tenant = tenantRepository.save(Tenant.builder()
                    .companyName("Postgres Enterprise " + UUID.randomUUID())
                    .status(TenantStatus.ACTIVE)
                    .plan(proPlan)
                    .build());

            Instant now = Instant.now();
            Subscription sub = subscriptionRepository.save(Subscription.builder()
                    .tenant(tenant)
                    .plan(proPlan)
                    .currentPeriodStart(now)
                    .currentPeriodEnd(now.plus(30, ChronoUnit.DAYS))
                    .build());

            assertThat(tenant.getId()).isNotNull();
            assertThat(sub.getId()).isNotNull();

            // Verify row-level lock execution (SELECT ... FOR UPDATE) against real PostgreSQL 16 engine
            Subscription lockedSub = subscriptionRepository.findByTenantIdWithLock(tenant.getId()).orElseThrow();
            assertThat(lockedSub.getPlan().getName()).isEqualTo("PRO");
            return null;
        });
    }
}
