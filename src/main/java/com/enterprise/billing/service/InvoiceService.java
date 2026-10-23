package com.enterprise.billing.service;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.domain.Invoice;
import com.enterprise.billing.domain.InvoiceStatus;
import com.enterprise.billing.domain.User;
import com.enterprise.billing.dto.InvoiceResponse;
import com.enterprise.billing.exception.ResourceNotFoundException;
import com.enterprise.billing.repository.InvoiceRepository;
import com.enterprise.billing.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> listInvoices(Pageable pageable) {
        UUID tenantId = getRequiredTenantId();
        return invoiceRepository.findAllByTenantId(tenantId, pageable)
                .map(InvoiceResponse::from);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(UUID id) {
        UUID tenantId = getRequiredTenantId();
        // Dual-verification: Must match authenticated tenant scope
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));

        return InvoiceResponse.from(invoice);
    }

    public InvoiceResponse payInvoice(UUID id) {
        UUID tenantId = getRequiredTenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalArgumentException("Invoice is already paid: " + id);
        }

        invoice.setStatus(InvoiceStatus.PAID);
        Invoice saved = invoiceRepository.save(invoice);

        UUID actorId = resolveCurrentActorId(tenantId);
        auditService.log(
                tenantId,
                actorId,
                "INVOICE_PAID",
                "INVOICE",
                saved.getId(),
                String.format("{\"amount\":%s,\"status\":\"PAID\"}", saved.getAmount())
        );

        return InvoiceResponse.from(saved);
    }

    private UUID getRequiredTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResourceNotFoundException("No active tenant context found for operation");
        }
        return tenantId;
    }

    private UUID resolveCurrentActorId(UUID tenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByTenantIdAndEmail(tenantId, auth.getName())
                    .map(User::getId)
                    .orElse(null);
        }
        return null;
    }
}
