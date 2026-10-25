package com.enterprise.billing.web;

import com.enterprise.billing.domain.IdempotencyRecord;
import com.enterprise.billing.domain.IdempotencyStatus;
import com.enterprise.billing.domain.Invoice;
import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.dto.RegisterTenantRequest;
import com.enterprise.billing.dto.UpgradeSubscriptionRequest;
import com.enterprise.billing.repository.IdempotencyRecordRepository;
import com.enterprise.billing.repository.InvoiceRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private record AuthContext(String token, UUID tenantId) {}

    private AuthContext registerTenant(String companyName, String email) throws Exception {
        RegisterTenantRequest reg = new RegisterTenantRequest(companyName, email, "Password123!", "FREE");
        MvcResult res = mockMvc.perform(post("/api/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return new AuthContext(root.get("token").asText(), UUID.fromString(root.get("tenantId").asText()));
    }

    @Test
    @DisplayName("Allows identical idempotency keys across different tenants without database conflict")
    void shouldAllowIdenticalIdempotencyKeyAcrossDifferentTenants() throws Exception {
        String sharedKey = "SHARED-KEY-" + UUID.randomUUID();

        // 1. Tenant A executes upgrade with sharedKey
        AuthContext tenantA = registerTenant("Alpha Corp", "admin@alpha.com");
        UpgradeSubscriptionRequest reqA = new UpgradeSubscriptionRequest("PRO", null);

        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + tenantA.token())
                        .header("X-Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("PRO"));

        // 2. Tenant B executes upgrade with exact same sharedKey
        AuthContext tenantB = registerTenant("Beta Corp", "admin@beta.com");
        UpgradeSubscriptionRequest reqB = new UpgradeSubscriptionRequest("PRO", null);

        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + tenantB.token())
                        .header("X-Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("PRO"));

        // 3. Verify each tenant has its own distinct invoice scoped to the idempotency key
        Optional<Invoice> invA = invoiceRepository.findByTenantIdAndIdempotencyKey(tenantA.tenantId(), sharedKey);
        Optional<Invoice> invB = invoiceRepository.findByTenantIdAndIdempotencyKey(tenantB.tenantId(), sharedKey);

        assertThat(invA).isPresent();
        assertThat(invB).isPresent();
        assertThat(invA.get().getId()).isNotEqualTo(invB.get().getId());
    }

    @Test
    @DisplayName("Rejects payload mutation on reused idempotency key with 409 Conflict Problem Details")
    void shouldRejectPayloadFingerprintMutationWith409Conflict() throws Exception {
        String key = "PAYLOAD-MUTATION-" + UUID.randomUUID();
        AuthContext tenant = registerTenant("Gamma Industries", "admin@gamma.com");

        // 1. Initial request with PRO plan
        UpgradeSubscriptionRequest req1 = new UpgradeSubscriptionRequest("PRO", null);
        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + tenant.token())
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("PRO"));

        // 2. Second request with same key but altered payload (ENTERPRISE plan)
        UpgradeSubscriptionRequest req2 = new UpgradeSubscriptionRequest("ENTERPRISE", null);
        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + tenant.token())
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("ERR_IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("Idempotency key reused with different request payload"));
    }

    @Test
    @DisplayName("Replays cached response for identical request without re-executing logic")
    void shouldReplayCachedResponseForIdenticalRequest() throws Exception {
        String key = "CACHED-REPLAY-" + UUID.randomUUID();
        AuthContext tenant = registerTenant("Delta Corp", "admin@delta.com");

        UpgradeSubscriptionRequest req = new UpgradeSubscriptionRequest("PRO", null);

        // First call
        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + tenant.token())
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("PRO"));

        // Replay call with exact same key and payload
        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + tenant.token())
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.name").value("PRO"));

        // Verify only 1 invoice was created
        long invoiceCount = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getTenant().getId().equals(tenant.tenantId()))
                .count();
        assertThat(invoiceCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Rejects concurrent in-flight request for same key with 409 Operation in Progress")
    void shouldRejectConcurrentInFlightRequestWith409() throws Exception {
        String key = "IN-FLIGHT-" + UUID.randomUUID();
        AuthContext tenant = registerTenant("Epsilon Services", "admin@epsilon.com");

        Tenant tenantEntity = tenantRepository.findById(tenant.tenantId()).orElseThrow();

        // Simulate an ongoing PROCESSING state
        IdempotencyRecord processingRecord = IdempotencyRecord.builder()
                .tenant(tenantEntity)
                .idempotencyKey(key)
                .endpoint("/api/subscriptions/upgrade")
                .requestHash("some-dummy-hash")
                .status(IdempotencyStatus.PROCESSING)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        idempotencyRecordRepository.saveAndFlush(processingRecord);

        // Attempt request with the in-flight key
        UpgradeSubscriptionRequest req = new UpgradeSubscriptionRequest("PRO", null);
        mockMvc.perform(post("/api/subscriptions/upgrade")
                        .header("Authorization", "Bearer " + tenant.token())
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("ERR_IDEMPOTENCY_IN_PROGRESS"))
                .andExpect(jsonPath("$.detail").value("An operation with this idempotency key is currently being processed"));
    }
}
