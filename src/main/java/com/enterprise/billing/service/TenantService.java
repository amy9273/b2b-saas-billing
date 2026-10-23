package com.enterprise.billing.service;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.dto.TenantResponse;
import com.enterprise.billing.exception.ResourceNotFoundException;
import com.enterprise.billing.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public TenantResponse getCurrentTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResourceNotFoundException("No active tenant context found");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        return TenantResponse.from(tenant);
    }
}
