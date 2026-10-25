package com.enterprise.billing.service;

import com.enterprise.billing.domain.IdempotencyRecord;
import com.enterprise.billing.domain.IdempotencyStatus;
import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.repository.IdempotencyRecordRepository;
import com.enterprise.billing.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final TenantRepository tenantRepository;

    public IdempotencyService(IdempotencyRecordRepository repository, TenantRepository tenantRepository) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findRecord(UUID tenantId, String idempotencyKey) {
        return repository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
    }

    @Transactional
    public IdempotencyRecord startProcessing(UUID tenantId, String idempotencyKey, String endpoint, String requestHash) {
        // If an expired record exists, remove it first
        repository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).ifPresent(existing -> {
            if (existing.getExpiresAt().isBefore(Instant.now())) {
                repository.delete(existing);
                repository.flush();
            }
        });

        Tenant tenantRef = tenantRepository.getReferenceById(tenantId);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(24, ChronoUnit.HOURS);

        IdempotencyRecord record = IdempotencyRecord.builder()
                .tenant(tenantRef)
                .idempotencyKey(idempotencyKey)
                .endpoint(endpoint)
                .requestHash(requestHash)
                .status(IdempotencyStatus.PROCESSING)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        return repository.saveAndFlush(record);
    }

    @Transactional
    public void markCompleted(UUID tenantId, String idempotencyKey, int responseStatus, String responseBody) {
        repository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseStatus(responseStatus);
            record.setResponseBody(responseBody);
            repository.saveAndFlush(record);
        });
    }

    @Transactional
    public void markFailedOrDelete(UUID tenantId, String idempotencyKey) {
        repository.deleteByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        repository.flush();
    }
}
