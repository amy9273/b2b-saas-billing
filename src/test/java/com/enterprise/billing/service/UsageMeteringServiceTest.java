package com.enterprise.billing.service;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.domain.ApiUsage;
import com.enterprise.billing.domain.Plan;
import com.enterprise.billing.domain.Subscription;
import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.dto.UsageSummaryResponse;
import com.enterprise.billing.repository.ApiUsageRepository;
import com.enterprise.billing.repository.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageMeteringServiceTest {

    @Mock
    private ApiUsageRepository apiUsageRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private UsageMeteringService usageMeteringService;

    private UUID tenantId;
    private Plan testPlan;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .companyName("Test Corp")
                .build();

        testPlan = Plan.builder()
                .id(1L)
                .name("PRO")
                .monthlyPrice(new BigDecimal("49.00"))
                .maxUsers(20)
                .maxApiCalls(3) // 3 calls allowed for testing recurring fraction 1/3
                .build();

        Instant now = Instant.now();
        subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .plan(testPlan)
                .currentPeriodStart(now.minusSeconds(86400))
                .currentPeriodEnd(now.plusSeconds(86400 * 29))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Usage percentage calculates exact BigDecimal scale 2 with RoundingMode.HALF_UP (1/3 calls = 33.33)")
    void shouldCalculateUsagePercentageWithExactBigDecimalScaleTwo() {
        LocalDate periodStart = subscription.getCurrentPeriodStart().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate periodEnd = subscription.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate();

        ApiUsage usage = ApiUsage.builder()
                .id(UUID.randomUUID())
                .tenant(subscription.getTenant())
                .callCount(1L)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));
        when(apiUsageRepository.findByTenantIdAndPeriodStartAndPeriodEnd(eq(tenantId), eq(periodStart), eq(periodEnd)))
                .thenReturn(Optional.of(usage));

        UsageSummaryResponse response = usageMeteringService.getUsageSummary();

        assertThat(response.callCount()).isEqualTo(1L);
        assertThat(response.maxApiCalls()).isEqualTo(3);
        assertThat(response.remainingCalls()).isEqualTo(2L);
        assertThat(response.usagePercentage()).isEqualByComparingTo("33.33");
        assertThat(response.usagePercentage().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Usage percentage handles 0 calls as 0.00 scale 2")
    void shouldHandleZeroUsage() {
        LocalDate periodStart = subscription.getCurrentPeriodStart().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate periodEnd = subscription.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));
        when(apiUsageRepository.findByTenantIdAndPeriodStartAndPeriodEnd(eq(tenantId), eq(periodStart), eq(periodEnd)))
                .thenReturn(Optional.empty());

        UsageSummaryResponse response = usageMeteringService.getUsageSummary();

        assertThat(response.callCount()).isZero();
        assertThat(response.usagePercentage()).isEqualByComparingTo("0.00");
        assertThat(response.usagePercentage().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Usage percentage handles 2/3 calls as 66.67 scale 2")
    void shouldHandleTwoThirdsUsage() {
        LocalDate periodStart = subscription.getCurrentPeriodStart().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate periodEnd = subscription.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate();

        ApiUsage usage = ApiUsage.builder()
                .id(UUID.randomUUID())
                .tenant(subscription.getTenant())
                .callCount(2L)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));
        when(apiUsageRepository.findByTenantIdAndPeriodStartAndPeriodEnd(eq(tenantId), eq(periodStart), eq(periodEnd)))
                .thenReturn(Optional.of(usage));

        UsageSummaryResponse response = usageMeteringService.getUsageSummary();

        assertThat(response.callCount()).isEqualTo(2L);
        assertThat(response.usagePercentage()).isEqualByComparingTo("66.67");
        assertThat(response.usagePercentage().scale()).isEqualTo(2);
    }
}
