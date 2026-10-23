package com.enterprise.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BillingEngineApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Spring Application Context loads and Flyway migrations execute cleanly")
    void contextLoads() {
        assertThat(jdbcTemplate).isNotNull();

        // Verify Flyway V2 seeded the 3 standard subscription tiers
        Integer planCount = jdbcTemplate.queryForObject("SELECT count(*) FROM plans", Integer.class);
        assertThat(planCount).isEqualTo(3);

        // Verify Free, Pro, and Enterprise tiers exist
        String freePlan = jdbcTemplate.queryForObject(
                "SELECT name FROM plans WHERE name = 'FREE'", String.class);
        assertThat(freePlan).isEqualTo("FREE");

        String proPlan = jdbcTemplate.queryForObject(
                "SELECT name FROM plans WHERE name = 'PRO'", String.class);
        assertThat(proPlan).isEqualTo("PRO");

        String enterprisePlan = jdbcTemplate.queryForObject(
                "SELECT name FROM plans WHERE name = 'ENTERPRISE'", String.class);
        assertThat(enterprisePlan).isEqualTo("ENTERPRISE");

        // Verify multi-tenant core tables exist and can be queried
        Integer tenantCount = jdbcTemplate.queryForObject("SELECT count(*) FROM tenants", Integer.class);
        assertThat(tenantCount).isNotNull();

        Integer userCount = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertThat(userCount).isNotNull();

        Integer subscriptionCount = jdbcTemplate.queryForObject("SELECT count(*) FROM subscriptions", Integer.class);
        assertThat(subscriptionCount).isNotNull();

        Integer invoiceCount = jdbcTemplate.queryForObject("SELECT count(*) FROM invoices", Integer.class);
        assertThat(invoiceCount).isNotNull();

        Integer auditCount = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isNotNull();
    }
}
