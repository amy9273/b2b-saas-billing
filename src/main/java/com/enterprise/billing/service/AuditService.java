package com.enterprise.billing.service;

import com.enterprise.billing.domain.AuditLog;
import com.enterprise.billing.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(UUID tenantId, UUID actorId, String action, String entityType, UUID entityId, String changesJson) {
        AuditLog log = AuditLog.builder()
                .tenantId(tenantId)
                .actorId(actorId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .changesJson(changesJson)
                .createdAt(Instant.now())
                .build();

        auditLogRepository.save(log);
    }
}
