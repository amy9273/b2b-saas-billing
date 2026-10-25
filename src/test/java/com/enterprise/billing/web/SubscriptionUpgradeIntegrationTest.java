package com.enterprise.billing.web;

import com.enterprise.billing.domain.Invoice;
import com.enterprise.billing.domain.InvoiceStatus;
import com.enterprise.billing.domain.UserRole;
import com.enterprise.billing.dto.CreateUserRequest;
import com.enterprise.billing.dto.LoginRequest;
import com.enterprise.billing.dto.RegisterTenantRequest;
import com.enterprise.billing.dto.UpgradeSubscriptionRequest;
import com.enterprise.billing.repository.AuditLogRepository;
import com.enterprise.billing.repository.InvoiceRepository;
import com.enterprise.billing.repository.SubscriptionRepository;
import com.enterprise.billing.repository.TenantRepository;
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

import com.enterprise.billing.domain.Subscription;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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
class SubscriptionUpgradeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private String registerAndGetToken(String companyName, String email, String plan) throws Exception {
        RegisterTenantRequest request = new RegisterTenantRequest(companyName, email, "Password123!", plan);
        MvcResult result = mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.get("token").asText();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.get("token").asText();
    }

    @Test
    @DisplayName("GET /api/subscriptions/me returns active subscription details and remaining days")
    void shouldReturnActiveSubscriptionDetails() throws Exception {
        String token = registerAndGetToken("Acme SaaS", "admin@acme.com", "FREE");

        int expectedDays = (int) ChronoUnit.DAYS.between(
                LocalDate.now(ZoneOffset.UTC),
                LocalDate.now(ZoneOffset.UTC).plusMonths(1)
        );

        mockMvc.perform(get("/api/subscriptions/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("FREE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.daysRemaining").value(expectedDays));
    }

    @Test
    @DisplayName("POST /api/subscriptions/preview-proration calculates preview without mutating state")
    void shouldPreviewProrationWithoutStateMutation() throws Exception {
        String token = registerAndGetToken("Wayne Enterprises", "bruce@wayne.com", "FREE");

        int expectedDays = (int) ChronoUnit.DAYS.between(
                LocalDate.now(ZoneOffset.UTC),
                LocalDate.now(ZoneOffset.UTC).plusMonths(1)
        );

        UpgradeSubscriptionRequest previewReq = new UpgradeSubscriptionRequest("PRO", null);

        mockMvc.perform(post("/api/subscriptions/preview-proration")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oldPlanName").value("FREE"))
                .andExpect(jsonPath("$.newPlanName").value("PRO"))
                .andExpect(jsonPath("$.oldPlanPrice").value(0.00))
                .andExpect(jsonPath("$.newPlanPrice").value(49.00))
                .andExpect(jsonPath("$.daysRemaining").value(expectedDays))
                .andExpect(jsonPath("$.totalDays").value(expectedDays))
                .andExpect(jsonPath("$.proratedCredit").value(0.00))
                .andExpect(jsonPath("$.newPlanCharge").value(49.00))
                .andExpect(jsonPath("$.netDue").value(49.00));

        // Verify database subscription remains FREE
        mockMvc.perform(get("/api/subscriptions/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("FREE"));
    }

    @Test
    @DisplayName("Non-admin role cannot execute subscription upgrade (403 Forbidden)")
    void shouldBlockNonAdminFromUpgradingSubscription() throws Exception {
        String adminToken = registerAndGetToken("Cyberdyne Systems", "admin@cyberdyne.com", "FREE");

        // Invite a member user
        CreateUserRequest inviteReq = new CreateUserRequest("john@cyberdyne.com", "MemberPass123!", UserRole.ROLE_MEMBER);
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isCreated());

        String memberToken = loginAndGetToken("john@cyberdyne.com", "MemberPass123!");

        UpgradeSubscriptionRequest upgradeReq = new UpgradeSubscriptionRequest("PRO", null);

        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errorCode").value("ERR_FORBIDDEN"));
    }

    @Test
    @DisplayName("Downgrade guardrail rejects downgrade if active users exceed target tier quota (403 ERR_QUOTA_EXCEEDED)")
    void shouldRejectDowngradeWhenActiveUsersExceedTargetQuota() throws Exception {
        String adminToken = registerAndGetToken("LexCorp", "lex@lexcorp.com", "PRO");

        // Add 5 more users so total active users is 6 (1 admin + 5 members = 6, exceeding FREE plan limit of 5)
        for (int i = 1; i <= 5; i++) {
            CreateUserRequest inviteReq = new CreateUserRequest("member" + i + "@lexcorp.com", "Pass12345!", UserRole.ROLE_MEMBER);
            mockMvc.perform(post("/api/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(inviteReq)))
                    .andExpect(status().isCreated());
        }

        // Attempt downgrade from PRO to FREE
        UpgradeSubscriptionRequest downgradeReq = new UpgradeSubscriptionRequest("FREE", null);

        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(downgradeReq)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errorCode").value("ERR_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("exceed target plan limit")));
    }

    @Test
    @DisplayName("Successful upgrade emits invoice, logs audit trail, and updates tenant subscription")
    void shouldSuccessfullyUpgradeSubscription() throws Exception {
        String token = registerAndGetToken("Omni Consumer Products", "admin@ocp.com", "FREE");

        String idempotencyKey = UUID.randomUUID().toString();
        UpgradeSubscriptionRequest upgradeReq = new UpgradeSubscriptionRequest("PRO", idempotencyKey);

        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("PRO"))
                .andExpect(jsonPath("$.plan.maxUsers").value(25))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify updated subscription on GET /me
        MvcResult meResult = mockMvc.perform(get("/api/subscriptions/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("PRO"))
                .andReturn();

        JsonNode subJson = objectMapper.readTree(meResult.getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(subJson.get("tenantId").asText());

        // Verify Invoice generated in DB
        List<Invoice> invoices = invoiceRepository.findAllByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertThat(invoices).hasSize(1);
        Invoice invoice = invoices.get(0);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(invoice.getAmount()).isEqualByComparingTo("49.00");

        // Verify Audit Log
        assertThat(auditLogRepository.findAll()).anyMatch(log ->
                "SUBSCRIPTION_UPGRADED".equals(log.getAction()) && tenantId.equals(log.getTenantId())
        );
    }

    @Test
    @DisplayName("Idempotent replay returns existing subscription without duplicate invoice generation")
    void shouldHandleIdempotentUpgradeReplaySafely() throws Exception {
        String token = registerAndGetToken("Starlight Tech", "admin@starlight.com", "FREE");

        String idempotencyKey = "idemp-" + UUID.randomUUID();
        UpgradeSubscriptionRequest upgradeReq = new UpgradeSubscriptionRequest("PRO", idempotencyKey);

        // First call
        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("PRO"));

        // Replay with identical idempotency key
        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("PRO"));

        // Verify exactly one invoice was created
        MvcResult meResult = mockMvc.perform(get("/api/subscriptions/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode subJson = objectMapper.readTree(meResult.getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(subJson.get("tenantId").asText());

        List<Invoice> invoices = invoiceRepository.findAllByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertThat(invoices).hasSize(1);
    }

    @Test
    @DisplayName("Upgrading to identical plan rejected with 400 Bad Request")
    void shouldRejectUpgradeToIdenticalPlan() throws Exception {
        String token = registerAndGetToken("Aperture Science", "glados@aperture.com", "PRO");

        UpgradeSubscriptionRequest upgradeReq = new UpgradeSubscriptionRequest("PRO", null);

        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_REQUEST"));
    }

    @Test
    @DisplayName("Upgrading to non-existent plan rejected with 404 Not Found")
    void shouldRejectUpgradeToNonExistentPlan() throws Exception {
        String token = registerAndGetToken("Initech", "peter@initech.com", "FREE");

        UpgradeSubscriptionRequest upgradeReq = new UpgradeSubscriptionRequest("NON_EXISTENT_PLAN", null);

        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errorCode").value("ERR_NOT_FOUND"));
    }

    @Test
    @DisplayName("Upgrading expired subscription triggers full cycle renewal, resets period window, and charges full plan price")
    void shouldRenewExpiredSubscriptionWithFullChargeAndNewPeriodWindow() throws Exception {
        String token = registerAndGetToken("Expired Corp", "admin@expiredcorp.com", "FREE");

        // Fetch subscription and set currentPeriodEnd to 2 days in the past
        MvcResult meResult = mockMvc.perform(get("/api/subscriptions/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode subJson = objectMapper.readTree(meResult.getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(subJson.get("tenantId").asText());

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId).orElseThrow();
        Instant expiredEnd = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant expiredStart = Instant.now().minus(32, ChronoUnit.DAYS);
        subscription.setCurrentPeriodStart(expiredStart);
        subscription.setCurrentPeriodEnd(expiredEnd);
        subscriptionRepository.saveAndFlush(subscription);

        // Verify preview reflects renewal with full price $299.00 and $0 credit
        UpgradeSubscriptionRequest previewReq = new UpgradeSubscriptionRequest("ENTERPRISE", null);
        mockMvc.perform(post("/api/subscriptions/preview-proration")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oldPlanName").value("FREE"))
                .andExpect(jsonPath("$.newPlanName").value("ENTERPRISE"))
                .andExpect(jsonPath("$.oldPlanPrice").value(0.00))
                .andExpect(jsonPath("$.proratedCredit").value(0.00))
                .andExpect(jsonPath("$.newPlanPrice").value(299.00))
                .andExpect(jsonPath("$.newPlanCharge").value(299.00))
                .andExpect(jsonPath("$.netDue").value(299.00));

        // Upgrade to ENTERPRISE
        String idempotencyKey = UUID.randomUUID().toString();
        UpgradeSubscriptionRequest upgradeReq = new UpgradeSubscriptionRequest("ENTERPRISE", idempotencyKey);

        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("ENTERPRISE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify database invoice was generated for full $299.00 (NOT $0.00 exploit)
        List<Invoice> invoices = invoiceRepository.findAllByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertThat(invoices).hasSize(1);
        Invoice invoice = invoices.get(0);
        assertThat(invoice.getAmount()).isEqualByComparingTo("299.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // Verify subscription dates were reset to new cycle starting now
        Subscription updatedSub = subscriptionRepository.findByTenantId(tenantId).orElseThrow();
        assertThat(updatedSub.getCurrentPeriodStart()).isAfter(expiredStart);
        assertThat(updatedSub.getCurrentPeriodEnd()).isAfter(Instant.now());
    }
}
