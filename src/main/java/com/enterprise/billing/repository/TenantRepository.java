package com.enterprise.billing.repository;

import com.enterprise.billing.domain.Tenant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsByCompanyNameIgnoreCase(String companyName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tenant t WHERE t.id = :id")
    Optional<Tenant> findByIdWithLock(@Param("id") UUID id);
}
