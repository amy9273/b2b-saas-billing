package com.enterprise.billing.security;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Problem Details")
    void shouldReturn401WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/test/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value("/api/test/secured"));
    }

    @Test
    @DisplayName("Request with invalid JWT returns 401 Problem Details")
    void shouldReturn401WhenTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/api/test/secured")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Request with valid JWT sets authentication, populates TenantContext, and cleans up context")
    void shouldAuthenticateAndPopulateTenantContext() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "ceo@acme-corp.com";
        String token = jwtService.generateToken(userId, tenantId, email, "ROLE_ADMIN");

        mockMvc.perform(get("/api/test/secured")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()));

        // Invariant #2: TenantContext must be cleared in finally block, leaving zero thread pollution
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("User with ROLE_MEMBER is forbidden from accessing ROLE_ADMIN endpoint (403 Problem Details)")
    void shouldReturn403WhenRoleIsInsufficient() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, tenantId, "member@acme.com", "ROLE_MEMBER");

        mockMvc.perform(get("/api/test/admin-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.errorCode").value("ERR_FORBIDDEN"));
    }

    @Test
    @DisplayName("User with ROLE_ADMIN successfully accesses ROLE_ADMIN endpoint")
    void shouldAllowAdminRoleAccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, tenantId, "admin@acme.com", "ROLE_ADMIN");

        mockMvc.perform(get("/api/test/admin-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("admin-access-granted"));
    }

    @Test
    @DisplayName("Unauthenticated request guarantees TenantContext cleanup even if thread had leftover state")
    void shouldEnsureThreadCleanlinessOnUnauthenticatedRequest() throws Exception {
        // Pre-pollute ThreadLocal before request executes
        TenantContext.setTenantId(UUID.randomUUID());
        assertThat(TenantContext.getTenantId()).isNotNull();

        try {
            mockMvc.perform(get("/api/plans"))
                    .andExpect(status().isOk());

            // Invariant #2: TenantContext must be completely cleared by filter finally block
            assertThat(TenantContext.getTenantId()).isNull();
        } finally {
            TenantContext.clear();
        }
    }
}
