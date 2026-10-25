package com.enterprise.billing.repository;

import com.enterprise.billing.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    void deleteByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
