package com.enterprise.billing.web;

import com.enterprise.billing.dto.ProrationPreviewResponse;
import com.enterprise.billing.dto.SubscriptionResponse;
import com.enterprise.billing.dto.UpgradeSubscriptionRequest;
import com.enterprise.billing.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponse> getSubscription() {
        return ResponseEntity.ok(subscriptionService.getSubscription());
    }

    @PostMapping("/preview-proration")
    public ResponseEntity<ProrationPreviewResponse> previewProration(
            @Valid @RequestBody UpgradeSubscriptionRequest request
    ) {
        return ResponseEntity.ok(subscriptionService.previewProration(request));
    }

    @PostMapping("/upgrade")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<SubscriptionResponse> upgradeSubscription(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String headerIdempotencyKey,
            @Valid @RequestBody UpgradeSubscriptionRequest request
    ) {
        SubscriptionResponse response = subscriptionService.upgradeSubscription(request, headerIdempotencyKey);
        return ResponseEntity.ok(response);
    }
}
