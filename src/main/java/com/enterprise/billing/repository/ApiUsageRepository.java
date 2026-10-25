package com.enterprise.billing.repository;

import com.enterprise.billing.domain.ApiUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiUsageRepository extends JpaRepository<ApiUsage, UUID> {

    Optional<ApiUsage> findByTenantIdAndPeriodStartAndPeriodEnd(UUID tenantId, LocalDate periodStart, LocalDate periodEnd);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ApiUsage a WHERE a.tenant.id = :tenantId AND a.periodStart = :start AND a.periodEnd = :end")
    Optional<ApiUsage> findByTenantIdAndPeriodStartAndPeriodEndWithLock(
            @Param("tenantId") UUID tenantId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Modifying
    @Query("UPDATE ApiUsage a SET a.callCount = a.callCount + 1, a.updatedAt = :now WHERE a.id = :id")
    int incrementCallCount(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query(value = """
        INSERT INTO api_usage (id, tenant_id, period_start, period_end, call_count, created_at, updated_at)
        VALUES (gen_random_uuid(), :tenantId, :start, :end, 1, :now, :now)
        ON CONFLICT (tenant_id, period_start, period_end)
        DO UPDATE SET call_count = api_usage.call_count + 1, updated_at = :now
        WHERE api_usage.call_count < :maxCalls
    """, nativeQuery = true)
    int recordUsageIfUnderQuota(@Param("tenantId") UUID tenantId, @Param("start") LocalDate start,
                                @Param("end") LocalDate end, @Param("maxCalls") int maxCalls,
                                @Param("now") Instant now);
}
