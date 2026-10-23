package com.enterprise.billing.service;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.domain.Plan;
import com.enterprise.billing.domain.Subscription;
import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.domain.TenantStatus;
import com.enterprise.billing.domain.User;
import com.enterprise.billing.domain.UserRole;
import com.enterprise.billing.dto.UpgradeSubscriptionRequest;
import com.enterprise.billing.repository.InvoiceRepository;
import com.enterprise.billing.repository.PlanRepository;
import com.enterprise.billing.repository.SubscriptionRepository;
import com.enterprise.billing.repository.TenantRepository;
import com.enterprise.billing.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
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
class SubscriptionConcurrencyTest {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Test
    @DisplayName("Simultaneous concurrent upgrade requests execute sequentially with row-level pessimistic lock")
    void testConcurrentUpgradesPreventRaceConditions() throws Exception {
        Plan freePlan = planRepository.findByNameIgnoreCase("FREE").orElseThrow();
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .companyName("Concurrent Corp " + UUID.randomUUID())
                .status(TenantStatus.ACTIVE)
                .plan(freePlan)
                .build());

        Instant now = Instant.now();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .tenant(tenant)
                .plan(freePlan)
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plus(30, ChronoUnit.DAYS))
                .build());

        userRepository.save(User.builder()
                .tenant(tenant)
                .email("admin-" + UUID.randomUUID() + "@concurrent.com")
                .passwordHash("hashed")
                .role(UserRole.ROLE_ADMIN)
                .build());

        int threadCount = 4;
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
                    subscriptionService.upgradeSubscription(new UpgradeSubscriptionRequest("PRO", null), null);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException ex) {
                    // Concurrency safety: threads blocked on row lock notice the updated plan and reject re-upgrade
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

        // Exactly one thread performed the upgrade from FREE to PRO
        assertThat(successCount.get()).isEqualTo(1);
        // The subsequent 3 threads sequentially acquired lock, detected already on PRO, and were safely rejected
        assertThat(rejectedCount.get()).isEqualTo(threadCount - 1);

        // Verify only 1 invoice was generated for this tenant
        long invoiceCount = invoiceRepository.findAllByTenantId(tenant.getId(), Pageable.unpaged()).getTotalElements();
        assertThat(invoiceCount).isEqualTo(1);

        // Verify final plan in DB is PRO
        Subscription finalSub = subscriptionRepository.findById(subscription.getId()).orElseThrow();
        assertThat(finalSub.getPlan().getName()).isEqualTo("PRO");
    }
}
