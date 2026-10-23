package com.enterprise.billing.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("TenantContext sets, retrieves, and clears active tenant ID cleanly")
    void shouldSetAndClearTenantId() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);

        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("TenantContext enforces strict thread isolation across concurrent threads")
    void shouldMaintainThreadIsolation() throws InterruptedException {
        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();

        TenantContext.setTenantId(tenant1);

        AtomicReference<UUID> childThreadTenant = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread = new Thread(() -> {
            TenantContext.setTenantId(tenant2);
            childThreadTenant.set(TenantContext.getTenantId());
            TenantContext.clear();
            latch.countDown();
        });

        thread.start();
        boolean completed = latch.await(2, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(childThreadTenant.get()).isEqualTo(tenant2);
        // Main thread must still retain tenant1
        assertThat(TenantContext.getTenantId()).isEqualTo(tenant1);
    }
}
