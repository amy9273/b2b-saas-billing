package com.enterprise.billing.service;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.domain.ApiUsage;
import com.enterprise.billing.domain.Plan;
import com.enterprise.billing.domain.Subscription;
import com.enterprise.billing.dto.UsageSummaryResponse;
import com.enterprise.billing.exception.QuotaExceededException;
import com.enterprise.billing.exception.ResourceNotFoundException;
import com.enterprise.billing.repository.ApiUsageRepository;
import com.enterprise.billing.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class UsageMeteringService {

    private final ApiUsageRepository apiUsageRepository;
    private final SubscriptionRepository subscriptionRepository;

    public UsageMeteringService(
            ApiUsageRepository apiUsageRepository,
            SubscriptionRepository subscriptionRepository
    ) {
        this.apiUsageRepository = apiUsageRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    public record QuotaCheckResult(int maxApiCalls, long remainingCalls) {}

    @Transactional(readOnly = true)
    public UsageSummaryResponse getUsageSummary() {
        UUID tenantId = getRequiredTenantId();
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Active subscription not found for tenant: " + tenantId));

        LocalDate periodStart = subscription.getCurrentPeriodStart().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate periodEnd = subscription.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate();
        Plan plan = subscription.getPlan();

        ApiUsage usage = apiUsageRepository.findByTenantIdAndPeriodStartAndPeriodEnd(tenantId, periodStart, periodEnd)
                .orElse(null);

        long callCount = usage != null ? usage.getCallCount() : 0L;
        int maxApiCalls = plan.getMaxApiCalls();
        long remainingCalls = Math.max(0, maxApiCalls - callCount);
        double usagePercentage = maxApiCalls > 0 ? (double) Math.round(((double) callCount / maxApiCalls) * 10000.0) / 100.0 : 0.0;

        return new UsageSummaryResponse(
                tenantId,
                plan.getName(),
                callCount,
                maxApiCalls,
                remainingCalls,
                usagePercentage,
                periodStart,
                periodEnd
        );
    }

    @Transactional
    public QuotaCheckResult recordAndCheckQuota(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId).orElse(null);
        if (subscription == null) {
            return new QuotaCheckResult(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        LocalDate periodStart = subscription.getCurrentPeriodStart().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate periodEnd = subscription.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate();
        Plan plan = subscription.getPlan();

        ApiUsage usage = apiUsageRepository.findByTenantIdAndPeriodStartAndPeriodEnd(tenantId, periodStart, periodEnd)
                .orElseGet(() -> apiUsageRepository.save(ApiUsage.builder()
                        .tenant(subscription.getTenant())
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .callCount(0)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()));

        if (usage.getCallCount() >= plan.getMaxApiCalls()) {
            throw new QuotaExceededException(String.format(
                    "Monthly API call limit of %d reached for '%s' tier. Upgrade your subscription tier to proceed.",
                    plan.getMaxApiCalls(), plan.getName()
            ));
        }

        apiUsageRepository.incrementCallCount(usage.getId(), Instant.now());
        long newCallCount = usage.getCallCount() + 1;
        long remaining = Math.max(0, plan.getMaxApiCalls() - newCallCount);

        return new QuotaCheckResult(plan.getMaxApiCalls(), remaining);
    }

    private UUID getRequiredTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResourceNotFoundException("No active tenant context found for operation");
        }
        return tenantId;
    }
}
