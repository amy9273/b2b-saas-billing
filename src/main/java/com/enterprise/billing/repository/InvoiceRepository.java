package com.enterprise.billing.repository;

import com.enterprise.billing.domain.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findAllByTenantId(UUID tenantId, Pageable pageable);

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.tenant.id = :tenantId")
    Optional<Invoice> findByIdAndTenantIdWithLock(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<Invoice> findByIdempotencyKey(String idempotencyKey);

    Optional<Invoice> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
