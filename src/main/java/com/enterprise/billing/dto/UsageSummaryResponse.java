package com.enterprise.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UsageSummaryResponse(
        UUID tenantId,
        String planName,
        long callCount,
        int maxApiCalls,
        long remainingCalls,
        BigDecimal usagePercentage,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd
) {
}
