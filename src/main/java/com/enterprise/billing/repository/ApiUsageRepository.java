package com.enterprise.billing.repository;

import com.enterprise.billing.domain.ApiUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiUsageRepository extends JpaRepository<ApiUsage, UUID> {

    Optional<ApiUsage> findByTenantIdAndPeriodStartAndPeriodEnd(UUID tenantId, LocalDate periodStart, LocalDate periodEnd);

    @Modifying
    @Query("UPDATE ApiUsage a SET a.callCount = a.callCount + 1, a.updatedAt = :now WHERE a.id = :id")
    int incrementCallCount(@Param("id") UUID id, @Param("now") java.time.Instant now);
}
