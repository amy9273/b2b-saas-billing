package com.enterprise.billing.security;

import com.enterprise.billing.context.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Test REST controller used to verify security filters and role authorization in integration tests.
 */
@RestController
@RequestMapping("/api/test")
public class TestSecurityController {

    @GetMapping("/secured")
    public ResponseEntity<Map<String, Object>> getSecuredResource(Principal principal) {
        UUID tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(Map.of(
                "message", "Authenticated successfully",
                "email", principal.getName(),
                "tenantId", tenantId != null ? tenantId.toString() : "null"
        ));
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, String>> getAdminOnlyResource() {
        return ResponseEntity.ok(Map.of("status", "admin-access-granted"));
    }
}
