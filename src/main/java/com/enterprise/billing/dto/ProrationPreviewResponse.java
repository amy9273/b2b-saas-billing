package com.enterprise.billing.dto;

import java.math.BigDecimal;

public record ProrationPreviewResponse(
        String oldPlanName,
        String newPlanName,
        long daysRemaining,
        long totalDays,
        BigDecimal oldPlanPrice,
        BigDecimal proratedCredit,
        BigDecimal newPlanPrice,
        BigDecimal newPlanCharge,
        BigDecimal netDue
) {
}
