package com.enterprise.billing.dto;

import com.enterprise.billing.domain.Invoice;
import com.enterprise.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID tenantId,
        BigDecimal amount,
        InvoiceStatus status,
        String idempotencyKey,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        Instant createdAt
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getTenant() != null ? invoice.getTenant().getId() : null,
                invoice.getAmount(),
                invoice.getStatus(),
                invoice.getIdempotencyKey(),
                invoice.getBillingPeriodStart(),
                invoice.getBillingPeriodEnd(),
                invoice.getCreatedAt()
        );
    }
}
