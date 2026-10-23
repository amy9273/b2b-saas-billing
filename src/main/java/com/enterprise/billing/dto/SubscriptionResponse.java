package com.enterprise.billing.dto;

import com.enterprise.billing.domain.Subscription;
import com.enterprise.billing.domain.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID tenantId,
        PlanResponse plan,
        SubscriptionStatus status,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        long daysRemaining
) {
    public static SubscriptionResponse from(Subscription subscription, long daysRemaining) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getTenant() != null ? subscription.getTenant().getId() : null,
                subscription.getPlan() != null ? PlanResponse.from(subscription.getPlan()) : null,
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                daysRemaining
        );
    }
}
