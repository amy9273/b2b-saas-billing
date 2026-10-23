package com.enterprise.billing.config;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.service.UsageMeteringService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Middleware enforcing runtime API usage limits per tenant based on their active subscription tier.
 * Increments usage metrics, injects X-RateLimit headers, and gates metered API execution.
 */
@Component
public class QuotaEnforcementInterceptor implements HandlerInterceptor {

    private final UsageMeteringService usageMeteringService;

    public QuotaEnforcementInterceptor(UsageMeteringService usageMeteringService) {
        this.usageMeteringService = usageMeteringService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // Unauthenticated or public routes bypass quota metering
            return true;
        }

        String path = request.getRequestURI();

        // Exempt management, auth, catalog, and subscription upgrade endpoints so tenants can self-heal/upgrade
        if (isExemptPath(path)) {
            return true;
        }

        UsageMeteringService.QuotaCheckResult result = usageMeteringService.recordAndCheckQuota(tenantId);
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.maxApiCalls()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingCalls()));

        return true;
    }

    private boolean isExemptPath(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/plans")
                || path.startsWith("/api/usage")
                || path.startsWith("/api/subscriptions")
                || path.startsWith("/api/test/")
                || path.startsWith("/actuator/");
    }
}
