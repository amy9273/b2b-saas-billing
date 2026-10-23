package com.enterprise.billing.web;

import com.enterprise.billing.dto.UsageSummaryResponse;
import com.enterprise.billing.service.UsageMeteringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageMeteringService usageMeteringService;

    public UsageController(UsageMeteringService usageMeteringService) {
        this.usageMeteringService = usageMeteringService;
    }

    @GetMapping("/summary")
    public ResponseEntity<UsageSummaryResponse> getUsageSummary() {
        return ResponseEntity.ok(usageMeteringService.getUsageSummary());
    }
}
