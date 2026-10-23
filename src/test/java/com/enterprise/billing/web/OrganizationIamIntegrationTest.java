package com.enterprise.billing.web;

import com.enterprise.billing.domain.UserRole;
import com.enterprise.billing.dto.CreateUserRequest;
import com.enterprise.billing.dto.LoginRequest;
import com.enterprise.billing.dto.RegisterTenantRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizationIamIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Public plan catalog lists all subscription tiers with quotas")
    void shouldListPublicPlans() throws Exception {
        mockMvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("FREE"))
                .andExpect(jsonPath("$[0].maxUsers").value(5))
                .andExpect(jsonPath("$[1].name").value("PRO"))
                .andExpect(jsonPath("$[1].maxUsers").value(25))
                .andExpect(jsonPath("$[2].name").value("ENTERPRISE"))
                .andExpect(jsonPath("$[2].maxUsers").value(9999));
    }

    @Test
    @DisplayName("Tenant registration provisions organization, admin user, and returns valid JWT")
    void shouldRegisterNewTenantSuccessfully() throws Exception {
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Stark Industries",
                "tony@stark.com",
                "securePassword123",
                "PRO"
        );

        mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.companyName").value("Stark Industries"))
                .andExpect(jsonPath("$.email").value("tony@stark.com"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.planName").value("PRO"));
    }

    @Test
    @DisplayName("Duplicate tenant registration returns 409 Conflict Problem Details")
    void shouldRejectDuplicateTenantRegistration() throws Exception {
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Wayne Enterprises",
                "bruce@wayne.com",
                "batmanSecret123",
                "ENTERPRISE"
        );

        // First registration succeeds
        mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate registration must fail with 409
        mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("ERR_DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("User login succeeds with valid credentials and fails on invalid credentials")
    void shouldHandleLoginFlow() throws Exception {
        // Register tenant first
        RegisterTenantRequest reg = new RegisterTenantRequest(
                "Oscorp",
                "norman@oscorp.com",
                "goblinFormula99",
                "FREE"
        );
        mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Login with correct credentials
        LoginRequest validLogin = new LoginRequest("norman@oscorp.com", "goblinFormula99");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value("norman@oscorp.com"));

        // Login with incorrect password
        LoginRequest badPassword = new LoginRequest("norman@oscorp.com", "wrongPassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Enforces Free tier seat limit (max 5) and rejects 6th user with 403 Quota Exceeded")
    void shouldEnforceFreeTierSeatQuota() throws Exception {
        // 1. Register Free Tier Tenant (Admin is User #1)
        RegisterTenantRequest reg = new RegisterTenantRequest(
                "Pym Technologies",
                "hank@pym.com",
                "antmanFormula123",
                "FREE"
        );
        MvcResult regResult = mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode regNode = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String adminToken = regNode.get("token").asText();

        // 2. Add Users 2, 3, 4, 5 (all must succeed)
        for (int i = 2; i <= 5; i++) {
            CreateUserRequest userReq = new CreateUserRequest(
                    "user" + i + "@pym.com",
                    "password1234",
                    UserRole.ROLE_MEMBER
            );
            mockMvc.perform(post("/api/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("user" + i + "@pym.com"));
        }

        // 3. Attempt to add User #6 (Free limit is 5 seats) -> MUST reject with 403 Quota Exceeded
        CreateUserRequest user6 = new CreateUserRequest(
                "user6@pym.com",
                "password1234",
                UserRole.ROLE_MEMBER
        );
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user6)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ERR_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Plan limit of 5 users reached")));
    }

    @Test
    @DisplayName("Guarantees strict tenant data isolation between two organizations")
    void shouldEnforceCrossTenantDataIsolation() throws Exception {
        // Register Tenant A
        RegisterTenantRequest regA = new RegisterTenantRequest(
                "Acme Corp",
                "alice@acme.com",
                "password12345",
                "PRO"
        );
        MvcResult resA = mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regA)))
                .andExpect(status().isCreated())
                .andReturn();
        String tokenA = objectMapper.readTree(resA.getResponse().getContentAsString()).get("token").asText();

        // Register Tenant B
        RegisterTenantRequest regB = new RegisterTenantRequest(
                "Beta Logistics",
                "bob@beta.com",
                "password12345",
                "PRO"
        );
        MvcResult resB = mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regB)))
                .andExpect(status().isCreated())
                .andReturn();
        String tokenB = objectMapper.readTree(resB.getResponse().getContentAsString()).get("token").asText();

        // Tenant A lists users -> only alice@acme.com
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("alice@acme.com"));

        // Tenant B lists users -> only bob@beta.com
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("bob@beta.com"));

        // Tenant A checks /api/tenants/me -> Acme Corp
        mockMvc.perform(get("/api/tenants/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Acme Corp"));

        // Tenant B checks /api/tenants/me -> Beta Logistics
        mockMvc.perform(get("/api/tenants/me")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Beta Logistics"));
    }
}
