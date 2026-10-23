package com.enterprise.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record UpgradeSubscriptionRequest(
        @NotBlank(message = "Target plan name is required")
        String targetPlanName,

        String idempotencyKey
) {
}
