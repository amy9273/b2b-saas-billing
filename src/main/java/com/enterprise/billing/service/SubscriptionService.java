package com.enterprise.billing.service;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.domain.Invoice;
import com.enterprise.billing.domain.InvoiceStatus;
import com.enterprise.billing.domain.Plan;
import com.enterprise.billing.domain.Subscription;
import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.domain.User;
import com.enterprise.billing.dto.ProrationPreviewResponse;
import com.enterprise.billing.dto.SubscriptionResponse;
import com.enterprise.billing.dto.UpgradeSubscriptionRequest;
import com.enterprise.billing.exception.QuotaExceededException;
import com.enterprise.billing.exception.ResourceNotFoundException;
import com.enterprise.billing.repository.InvoiceRepository;
import com.enterprise.billing.repository.PlanRepository;
import com.enterprise.billing.repository.SubscriptionRepository;
import com.enterprise.billing.repository.TenantRepository;
import com.enterprise.billing.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;
    private final ProrationService prorationService;
    private final AuditService auditService;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            TenantRepository tenantRepository,
            PlanRepository planRepository,
            UserRepository userRepository,
            InvoiceRepository invoiceRepository,
            ProrationService prorationService,
            AuditService auditService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.invoiceRepository = invoiceRepository;
        this.prorationService = prorationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription() {
        UUID tenantId = getRequiredTenantId();
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for tenant: " + tenantId));

        long daysRemaining = prorationService.calculateDaysRemaining(subscription.getCurrentPeriodEnd(), Instant.now());
        return SubscriptionResponse.from(subscription, daysRemaining);
    }

    @Transactional(readOnly = true)
    public ProrationPreviewResponse previewProration(UpgradeSubscriptionRequest request) {
        UUID tenantId = getRequiredTenantId();
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for tenant: " + tenantId));

        Plan targetPlan = planRepository.findByNameIgnoreCase(request.targetPlanName())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + request.targetPlanName()));

        return prorationService.calculate(
                subscription.getPlan(),
                targetPlan,
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd()
        );
    }

    public SubscriptionResponse upgradeSubscription(UpgradeSubscriptionRequest request, String headerIdempotencyKey) {
        UUID tenantId = getRequiredTenantId();

        // 1. Resolve Idempotency Key (header takes precedence, then request body)
        String idempotencyKey = (headerIdempotencyKey != null && !headerIdempotencyKey.isBlank())
                ? headerIdempotencyKey.trim()
                : (request.idempotencyKey() != null && !request.idempotencyKey().isBlank() ? request.idempotencyKey().trim() : null);

        // 2. Acquire pessimistic lock on subscription to prevent concurrent race conditions
        Subscription subscription = subscriptionRepository.findByTenantIdWithLock(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for tenant: " + tenantId));

        // 4. Verify target plan exists
        Plan targetPlan = planRepository.findByNameIgnoreCase(request.targetPlanName())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + request.targetPlanName()));

        // 3. Prevent no-op change to the exact same plan, but safely replay if an invoice exists for this key
        if (subscription.getPlan().getId().equals(targetPlan.getId())) {
            if (idempotencyKey != null && invoiceRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).isPresent()) {
                long daysRemaining = prorationService.calculateDaysRemaining(subscription.getCurrentPeriodEnd(), Instant.now());
                return SubscriptionResponse.from(subscription, daysRemaining);
            }
            throw new IllegalArgumentException("Tenant is already subscribed to plan: " + targetPlan.getName());
        }

        // 6. Downgrade Quota Validation Guardrail: Prevent downgrade if active users exceed target tier
        long activeUsers = userRepository.countByTenantId(tenantId);
        if (activeUsers > targetPlan.getMaxUsers()) {
            throw new QuotaExceededException(String.format(
                    "Cannot downgrade to plan '%s'. Current active users (%d) exceed target plan limit (%d). Please offboard users before downgrading.",
                    targetPlan.getName(), activeUsers, targetPlan.getMaxUsers()
            ));
        }

        // 7. Calculate deterministic proration breakdown
        Instant now = Instant.now();
        Plan oldPlan = subscription.getPlan();
        ProrationPreviewResponse proration = prorationService.calculate(
                oldPlan,
                targetPlan,
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                now
        );

        // 8. Update Subscription and Tenant plan bindings
        subscription.setPlan(targetPlan);
        subscriptionRepository.save(subscription);

        Tenant tenant = subscription.getTenant();
        tenant.setPlan(targetPlan);
        tenantRepository.save(tenant);

        // 9. Generate immutable invoice record
        LocalDate periodStart = subscription.getCurrentPeriodStart().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate periodEnd = subscription.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate();

        Invoice invoice = Invoice.builder()
                .tenant(tenant)
                .amount(proration.netDue())
                .status(InvoiceStatus.PAID)
                .idempotencyKey(idempotencyKey)
                .billingPeriodStart(periodStart)
                .billingPeriodEnd(periodEnd)
                .createdAt(now)
                .build();
        Invoice savedInvoice = invoiceRepository.save(invoice);

        // 10. Record immutable audit log
        UUID actorId = resolveCurrentActorId(tenantId);
        String changesJson = String.format(
                "{\"oldPlan\":\"%s\",\"newPlan\":\"%s\",\"netDue\":%s,\"invoiceId\":\"%s\"}",
                oldPlan.getName(), targetPlan.getName(), proration.netDue(), savedInvoice.getId()
        );
        auditService.log(
                tenantId,
                actorId,
                "SUBSCRIPTION_UPGRADED",
                "SUBSCRIPTION",
                subscription.getId(),
                changesJson
        );

        return SubscriptionResponse.from(subscription, proration.daysRemaining());
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
