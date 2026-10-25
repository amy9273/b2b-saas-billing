package com.enterprise.billing.web;

import com.enterprise.billing.dto.InvoiceResponse;
import com.enterprise.billing.service.InvoiceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public ResponseEntity<Page<InvoiceResponse>> listInvoices(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(invoiceService.listInvoices(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(id));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<InvoiceResponse> payInvoice(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(invoiceService.payInvoice(id));
    }
}
