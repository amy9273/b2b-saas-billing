package com.enterprise.billing.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UsageSummaryResponse(
        UUID tenantId,
        String planName,
        long callCount,
        int maxApiCalls,
        long remainingCalls,
        double usagePercentage,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd
) {
}
