package com.enterprise.billing.dto;

import com.enterprise.billing.domain.Plan;

import java.math.BigDecimal;

public record PlanResponse(
        Long id,
        String name,
        BigDecimal monthlyPrice,
        int maxUsers,
        int maxApiCalls
) {
    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getMonthlyPrice(),
                plan.getMaxUsers(),
                plan.getMaxApiCalls()
        );
    }
}
