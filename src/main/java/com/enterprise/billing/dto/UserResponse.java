package com.enterprise.billing.dto;

import com.enterprise.billing.domain.User;
import com.enterprise.billing.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID tenantId,
        String email,
        UserRole role,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getTenant().getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
