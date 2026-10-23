package com.enterprise.billing.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized RFC 7807 Problem Details response representation.
 */
public record ProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Instant timestamp,
        String errorCode,
        Map<String, Object> metadata
) {
    public static ProblemDetails of(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            String errorCode,
            Map<String, Object> metadata
    ) {
        return new ProblemDetails(
                type,
                title,
                status,
                detail,
                instance,
                Instant.now(),
                errorCode,
                metadata != null ? metadata : Map.of()
        );
    }

    public static ProblemDetails of(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            String errorCode
    ) {
        return of(type, title, status, detail, instance, errorCode, Map.of());
    }
}
