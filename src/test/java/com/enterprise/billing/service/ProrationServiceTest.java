package com.enterprise.billing.service;

import com.enterprise.billing.domain.Plan;
import com.enterprise.billing.dto.ProrationPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ProrationServiceTest {

    private ProrationService prorationService;

    private Plan freePlan;
    private Plan proPlan;
    private Plan enterprisePlan;

    @BeforeEach
    void setUp() {
        prorationService = new ProrationService();

        freePlan = Plan.builder()
                .id(1L)
                .name("FREE")
                .monthlyPrice(new BigDecimal("0.00"))
                .maxUsers(5)
                .maxApiCalls(1000)
                .build();

        proPlan = Plan.builder()
                .id(2L)
                .name("PRO")
                .monthlyPrice(new BigDecimal("49.00"))
                .maxUsers(20)
                .maxApiCalls(50000)
                .build();

        enterprisePlan = Plan.builder()
                .id(3L)
                .name("ENTERPRISE")
                .monthlyPrice(new BigDecimal("199.00"))
                .maxUsers(500)
                .maxApiCalls(1000000)
                .build();
    }

    @Test
    @DisplayName("Mid-cycle upgrade from FREE to PRO with 15 of 30 days remaining")
    void testUpgradeFreeToProMidCycle() {
        Instant periodStart = LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant periodEnd = LocalDate.of(2026, 10, 31).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant calcTime = LocalDate.of(2026, 10, 16).atStartOfDay().toInstant(ZoneOffset.UTC);

        ProrationPreviewResponse preview = prorationService.calculate(
                freePlan, proPlan, periodStart, periodEnd, calcTime
        );

        assertThat(preview.oldPlanName()).isEqualTo("FREE");
        assertThat(preview.newPlanName()).isEqualTo("PRO");
        assertThat(preview.totalDays()).isEqualTo(30);
        assertThat(preview.daysRemaining()).isEqualTo(15);
        assertThat(preview.oldPlanPrice()).isEqualByComparingTo("0.00");
        assertThat(preview.proratedCredit()).isEqualByComparingTo("0.00");
        assertThat(preview.newPlanPrice()).isEqualByComparingTo("49.00");
        assertThat(preview.newPlanCharge()).isEqualByComparingTo("24.50");
        assertThat(preview.netDue()).isEqualByComparingTo("24.50");
    }

    @Test
    @DisplayName("Mid-cycle upgrade from PRO to ENTERPRISE with 10 of 30 days remaining")
    void testUpgradeProToEnterpriseMidCycle() {
        Instant periodStart = LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant periodEnd = LocalDate.of(2026, 10, 31).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant calcTime = LocalDate.of(2026, 10, 21).atStartOfDay().toInstant(ZoneOffset.UTC);

        ProrationPreviewResponse preview = prorationService.calculate(
                proPlan, enterprisePlan, periodStart, periodEnd, calcTime
        );

        // 10 days remaining / 30 total days = 0.3333333333
        // Credit = 49.00 * (10/30) = 16.33
        // Charge = 199.00 * (10/30) = 66.33
        // Net due = 66.33 - 16.33 = 50.00
        assertThat(preview.totalDays()).isEqualTo(30);
        assertThat(preview.daysRemaining()).isEqualTo(10);
        assertThat(preview.oldPlanPrice()).isEqualByComparingTo("49.00");
        assertThat(preview.proratedCredit()).isEqualByComparingTo("16.33");
        assertThat(preview.newPlanPrice()).isEqualByComparingTo("199.00");
        assertThat(preview.newPlanCharge()).isEqualByComparingTo("66.33");
        assertThat(preview.netDue()).isEqualByComparingTo("50.00");
        assertThat(preview.netDue().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Downgrade from PRO to FREE caps net due at 0.00")
    void testDowngradeProToFreeMidCycle() {
        Instant periodStart = LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant periodEnd = LocalDate.of(2026, 10, 31).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant calcTime = LocalDate.of(2026, 10, 16).atStartOfDay().toInstant(ZoneOffset.UTC);

        ProrationPreviewResponse preview = prorationService.calculate(
                proPlan, freePlan, periodStart, periodEnd, calcTime
        );

        assertThat(preview.totalDays()).isEqualTo(30);
        assertThat(preview.daysRemaining()).isEqualTo(15);
        assertThat(preview.proratedCredit()).isEqualByComparingTo("24.50");
        assertThat(preview.newPlanCharge()).isEqualByComparingTo("0.00");
        assertThat(preview.netDue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Zero days remaining results in 0.00 net due")
    void testZeroDaysRemaining() {
        Instant periodStart = LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant periodEnd = LocalDate.of(2026, 10, 31).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant calcTime = LocalDate.of(2026, 11, 1).atStartOfDay().toInstant(ZoneOffset.UTC);

        ProrationPreviewResponse preview = prorationService.calculate(
                proPlan, enterprisePlan, periodStart, periodEnd, calcTime
        );

        assertThat(preview.daysRemaining()).isZero();
        assertThat(preview.proratedCredit()).isEqualByComparingTo("0.00");
        assertThat(preview.newPlanCharge()).isEqualByComparingTo("0.00");
        assertThat(preview.netDue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Full period remaining results in full price difference")
    void testFullPeriodRemaining() {
        Instant periodStart = LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant periodEnd = LocalDate.of(2026, 10, 31).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant calcTime = LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC);

        ProrationPreviewResponse preview = prorationService.calculate(
                proPlan, enterprisePlan, periodStart, periodEnd, calcTime
        );

        assertThat(preview.daysRemaining()).isEqualTo(30);
        assertThat(preview.totalDays()).isEqualTo(30);
        assertThat(preview.proratedCredit()).isEqualByComparingTo("49.00");
        assertThat(preview.newPlanCharge()).isEqualByComparingTo("199.00");
        assertThat(preview.netDue()).isEqualByComparingTo("150.00");
    }
}
