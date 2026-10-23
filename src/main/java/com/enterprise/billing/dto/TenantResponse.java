package com.enterprise.billing.dto;

import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.domain.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String companyName,
        TenantStatus status,
        PlanResponse plan,
        Instant createdAt
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getCompanyName(),
                tenant.getStatus(),
                tenant.getPlan() != null ? PlanResponse.from(tenant.getPlan()) : null,
                tenant.getCreatedAt()
        );
    }
}
