package com.enterprise.billing.concurrency;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.domain.AuditLog;
import com.enterprise.billing.domain.Invoice;
import com.enterprise.billing.domain.InvoiceStatus;
import com.enterprise.billing.domain.Plan;
import com.enterprise.billing.domain.Subscription;
import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.domain.TenantStatus;
import com.enterprise.billing.domain.User;
import com.enterprise.billing.domain.UserRole;
import com.enterprise.billing.dto.CreateUserRequest;
import com.enterprise.billing.exception.QuotaExceededException;
import com.enterprise.billing.repository.ApiUsageRepository;
import com.enterprise.billing.repository.AuditLogRepository;
import com.enterprise.billing.repository.InvoiceRepository;
import com.enterprise.billing.repository.PlanRepository;
import com.enterprise.billing.repository.SubscriptionRepository;
import com.enterprise.billing.repository.TenantRepository;
import com.enterprise.billing.repository.UserRepository;
import com.enterprise.billing.service.InvoiceService;
import com.enterprise.billing.service.UsageMeteringService;
import com.enterprise.billing.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuotaAndPaymentConcurrencyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private UsageMeteringService usageMeteringService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ApiUsageRepository apiUsageRepository;

    @AfterEach
    void tearDown() {
        apiUsageRepository.deleteAll();
        auditLogRepository.deleteAll();
        invoiceRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrent seat allocations on Free plan (capacity 5) admit exactly 1 of 10 concurrent invites")
    void testConcurrentSeatAllocationDoesNotExceedPlanLimit() throws Exception {
        Plan freePlan = planRepository.findByNameIgnoreCase("FREE").orElseThrow();
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .companyName("Seat Concurrency " + UUID.randomUUID())
                .status(TenantStatus.ACTIVE)
                .plan(freePlan)
                .build());

        // Pre-allocate 4 users (plan limit is 5, so exactly 1 slot remains)
        for (int i = 1; i <= 4; i++) {
            userRepository.save(User.builder()
                    .tenant(tenant)
                    .email("existing-user-" + i + "-" + UUID.randomUUID() + "@test.com")
                    .passwordHash("password")
                    .role(UserRole.ROLE_MEMBER)
                    .build());
        }

        assertThat(userRepository.countByTenantId(tenant.getId())).isEqualTo(4);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger quotaExceededCount = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    TenantContext.setTenantId(tenant.getId());
                    userService.createUser(new CreateUserRequest(
                            "concurrent-user-" + index + "-" + UUID.randomUUID() + "@test.com",
                            "SecurePass123!",
                            UserRole.ROLE_MEMBER
                    ));
                    successCount.incrementAndGet();
                } catch (QuotaExceededException ex) {
                    quotaExceededCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    TenantContext.clear();
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly 1 thread successfully claims the 5th seat
        assertThat(successCount.get()).isEqualTo(1);
        // The other 9 threads are rejected with QuotaExceededException
        assertThat(quotaExceededCount.get()).isEqualTo(threadCount - 1);

        // Invariant: Final user count must equal exactly the plan limit of 5
        long finalUserCount = userRepository.countByTenantId(tenant.getId());
        assertThat(finalUserCount).isEqualTo(5);
    }

    @Test
    @DisplayName("Concurrent invoice pay requests process exactly once and reject redundant attempts")
    void testConcurrentInvoicePaymentPreventsDuplicateExecution() throws Exception {
        Plan freePlan = planRepository.findByNameIgnoreCase("FREE").orElseThrow();
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .companyName("Pay Concurrency " + UUID.randomUUID())
                .status(TenantStatus.ACTIVE)
                .plan(freePlan)
                .build());

        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .tenant(tenant)
                .amount(new BigDecimal("49.00"))
                .status(InvoiceStatus.PENDING)
                .billingPeriodStart(LocalDate.now())
                .billingPeriodEnd(LocalDate.now().plusDays(30))
                .build());

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    TenantContext.setTenantId(tenant.getId());
                    invoiceService.payInvoice(invoice.getId());
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException ex) {
                    rejectedCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    TenantContext.clear();
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly 1 thread successfully marks the invoice PAID
        assertThat(successCount.get()).isEqualTo(1);
        // The remaining 4 threads acquire lock, see status PAID, and are rejected
        assertThat(rejectedCount.get()).isEqualTo(threadCount - 1);

        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // Exactly 1 audit log must be recorded for INVOICE_PAID
        List<AuditLog> auditLogs = auditLogRepository.findAll().stream()
                .filter(log -> "INVOICE_PAID".equals(log.getAction()) && invoice.getId().equals(log.getEntityId()))
                .toList();
        assertThat(auditLogs).hasSize(1);
    }

    @Test
    @DisplayName("Concurrent initial API usage calls correctly serialize without duplicate row violations")
    void testConcurrentUsageMeteringPreventsDuplicateInitialInsert() throws Exception {
        Plan freePlan = planRepository.findByNameIgnoreCase("FREE").orElseThrow();
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .companyName("Metering Concurrency " + UUID.randomUUID())
                .status(TenantStatus.ACTIVE)
                .plan(freePlan)
                .build());

        Instant now = Instant.now();
        subscriptionRepository.save(Subscription.builder()
                .tenant(tenant)
                .plan(freePlan)
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plus(30, ChronoUnit.DAYS))
                .build());

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    usageMeteringService.recordAndCheckQuota(tenant.getId());
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // All 10 requests successfully metered
        assertThat(successCount.get()).isEqualTo(threadCount);

        // Exactly 1 usage row exists for the tenant, with call count = 10
        long usageRowCount = apiUsageRepository.findAll().stream()
                .filter(u -> u.getTenant().getId().equals(tenant.getId()))
                .count();
        assertThat(usageRowCount).isEqualTo(1);

        long callCount = apiUsageRepository.findAll().stream()
                .filter(u -> u.getTenant().getId().equals(tenant.getId()))
                .findFirst()
                .orElseThrow()
                .getCallCount();
        assertThat(callCount).isEqualTo(threadCount);
    }
}
