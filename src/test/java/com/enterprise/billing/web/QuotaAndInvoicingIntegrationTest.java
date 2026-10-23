package com.enterprise.billing.web;

import com.enterprise.billing.domain.ApiUsage;
import com.enterprise.billing.domain.Invoice;
import com.enterprise.billing.domain.InvoiceStatus;
import com.enterprise.billing.dto.LoginRequest;
import com.enterprise.billing.dto.RegisterTenantRequest;
import com.enterprise.billing.dto.UpgradeSubscriptionRequest;
import com.enterprise.billing.repository.ApiUsageRepository;
import com.enterprise.billing.repository.AuditLogRepository;
import com.enterprise.billing.repository.InvoiceRepository;
import com.enterprise.billing.repository.SubscriptionRepository;
import com.enterprise.billing.repository.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuotaAndInvoicingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiUsageRepository apiUsageRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void tearDown() {
        apiUsageRepository.deleteAll();
        invoiceRepository.deleteAll();
    }

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

    @Test
    @DisplayName("GET /api/usage/summary returns current usage metrics and billing period boundaries")
    void shouldReturnUsageSummary() throws Exception {
        String token = registerAndGetToken("Acme Labs", "admin@acmelabs.com", "FREE");

        mockMvc.perform(get("/api/usage/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value("FREE"))
                .andExpect(jsonPath("$.callCount").value(0))
                .andExpect(jsonPath("$.maxApiCalls").value(1000))
                .andExpect(jsonPath("$.remainingCalls").value(1000))
                .andExpect(jsonPath("$.usagePercentage").value(0.0));
    }

    @Test
    @DisplayName("Metered endpoints increment call count and set rate limit response headers")
    void shouldIncrementUsageAndSetRateLimitHeaders() throws Exception {
        String token = registerAndGetToken("Global Dynamics", "fargo@globaldynamics.com", "FREE");

        // First call to a metered business endpoint (/api/tenants/me)
        mockMvc.perform(get("/api/tenants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "1000"))
                .andExpect(header().string("X-RateLimit-Remaining", "999"));

        // Second call
        mockMvc.perform(get("/api/tenants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "1000"))
                .andExpect(header().string("X-RateLimit-Remaining", "998"));

        // Verify summary reflects 2 consumed calls
        mockMvc.perform(get("/api/usage/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callCount").value(2))
                .andExpect(jsonPath("$.remainingCalls").value(998));
    }

    @Test
    @DisplayName("Gating: Tenant reaching monthly API limit is blocked with 403 ERR_QUOTA_EXCEEDED on metered routes")
    void shouldBlockMeteredRequestsWhenQuotaExceeded() throws Exception {
        String token = registerAndGetToken("Massive Dynamic", "walter@massivedynamic.com", "FREE");

        // Trigger initial usage record creation
        mockMvc.perform(get("/api/tenants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Exhaust the quota directly in database (set call_count to 1000 for FREE tier)
        ApiUsage usage = apiUsageRepository.findAll().get(0);
        usage.setCallCount(1000);
        apiUsageRepository.save(usage);

        // Metered endpoint must now be rejected with 403 Forbidden
        mockMvc.perform(get("/api/tenants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errorCode").value("ERR_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Monthly API call limit of 1000 reached")));

        // Exempt routes like /api/usage/summary must remain accessible so tenant can inspect quota
        mockMvc.perform(get("/api/usage/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callCount").value(1000))
                .andExpect(jsonPath("$.remainingCalls").value(0));

        // Exempt upgrade route allows tenant to upgrade to PRO
        UpgradeSubscriptionRequest upgradeReq = new UpgradeSubscriptionRequest("PRO", null);
        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("PRO"));

        // With PRO tier (limit 50,000), metered route is now unblocked!
        mockMvc.perform(get("/api/tenants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "50000"));
    }

    @Test
    @DisplayName("Invoicing: GET /api/invoices returns paginated list of tenant's invoices")
    void shouldListTenantInvoices() throws Exception {
        String token = registerAndGetToken("Initech Corp", "peter@initech.com", "FREE");

        // Trigger upgrade to generate an invoice
        UpgradeSubscriptionRequest upgradeReq = new UpgradeSubscriptionRequest("PRO", "inv-key-" + UUID.randomUUID());
        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isOk());

        // Fetch invoice list
        mockMvc.perform(get("/api/invoices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(49.00))
                .andExpect(jsonPath("$.content[0].status").value("PAID"));
    }

    @Test
    @DisplayName("Dual-verification: Tenant cannot access another tenant's invoice (404 Not Found)")
    void shouldEnforceDualVerificationForInvoices() throws Exception {
        String tokenA = registerAndGetToken("Company Alpha", "admin@alpha.com", "FREE");
        String tokenB = registerAndGetToken("Company Beta", "admin@beta.com", "FREE");

        // Company A generates an invoice via upgrade
        UpgradeSubscriptionRequest upgradeReq = new UpgradeSubscriptionRequest("PRO", "alpha-inv-" + UUID.randomUUID());
        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isOk());

        MvcResult listResult = mockMvc.perform(get("/api/invoices")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listJson = objectMapper.readTree(listResult.getResponse().getContentAsString());
        String invoiceId = listJson.get("content").get(0).get("id").asText();

        // Company A accesses its own invoice -> 200 OK
        mockMvc.perform(get("/api/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invoiceId));

        // Company B attempts to access Company A's invoice -> 404 Not Found
        mockMvc.perform(get("/api/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errorCode").value("ERR_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/invoices/{id}/pay marks PENDING invoice as PAID and emits AuditLog")
    void shouldPayPendingInvoiceSuccessfully() throws Exception {
        String token = registerAndGetToken("Hooli", "gavin@hooli.com", "FREE");

        MvcResult meResult = mockMvc.perform(get("/api/subscriptions/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode subJson = objectMapper.readTree(meResult.getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(subJson.get("tenantId").asText());

        // Create a manual PENDING invoice for this tenant
        Invoice pendingInvoice = invoiceRepository.save(Invoice.builder()
                .tenant(tenantRepository.findById(tenantId).orElseThrow())
                .amount(new BigDecimal("150.00"))
                .status(InvoiceStatus.PENDING)
                .billingPeriodStart(LocalDate.now(ZoneOffset.UTC))
                .billingPeriodEnd(LocalDate.now(ZoneOffset.UTC).plusDays(30))
                .createdAt(Instant.now())
                .build());

        // Pay invoice
        mockMvc.perform(post("/api/invoices/" + pendingInvoice.getId() + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amount").value(150.00));

        // Verify state in database
        Invoice paidInvoice = invoiceRepository.findById(pendingInvoice.getId()).orElseThrow();
        assertThat(paidInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // Verify Audit Log
        assertThat(auditLogRepository.findAll()).anyMatch(log ->
                "INVOICE_PAID".equals(log.getAction()) && tenantId.equals(log.getTenantId())
        );

        // Re-paying already paid invoice rejected with 400 Bad Request
        mockMvc.perform(post("/api/invoices/" + pendingInvoice.getId() + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_REQUEST"));
    }
}
