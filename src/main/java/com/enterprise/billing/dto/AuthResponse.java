package com.enterprise.billing.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        String tokenType,
        UUID userId,
        UUID tenantId,
        String email,
        String role,
        String companyName,
        String planName
) {
    public static AuthResponse bearer(
            String token,
            UUID userId,
            UUID tenantId,
            String email,
            String role,
            String companyName,
            String planName
    ) {
        return new AuthResponse(
                token,
                "Bearer",
                userId,
                tenantId,
                email,
                role,
                companyName,
                planName
        );
    }
}
