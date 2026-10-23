package com.enterprise.billing.context;

import java.util.UUID;

/**
 * ThreadLocal context holding the active tenant ID for the current request execution.
 * Guarantees logical isolation so downstream queries and business logic operate strictly
 * within the authenticated tenant's scope.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
