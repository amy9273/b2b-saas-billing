package com.enterprise.billing.service;

import com.enterprise.billing.domain.Plan;
import com.enterprise.billing.domain.Subscription;
import com.enterprise.billing.domain.SubscriptionStatus;
import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.domain.TenantStatus;
import com.enterprise.billing.domain.User;
import com.enterprise.billing.domain.UserRole;
import com.enterprise.billing.dto.AuthResponse;
import com.enterprise.billing.dto.LoginRequest;
import com.enterprise.billing.dto.RegisterTenantRequest;
import com.enterprise.billing.exception.DuplicateResourceException;
import com.enterprise.billing.exception.InvalidCredentialsException;
import com.enterprise.billing.repository.SubscriptionRepository;
import com.enterprise.billing.repository.TenantRepository;
import com.enterprise.billing.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Transactional
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            PlanService planService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planService = planService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    public AuthResponse registerTenant(RegisterTenantRequest request) {
        String companyName = request.companyName().trim();
        if (tenantRepository.existsByCompanyNameIgnoreCase(companyName)) {
            throw new DuplicateResourceException("Company name is already registered: " + companyName);
        }

        String targetPlan = request.planName() != null && !request.planName().isBlank()
                ? request.planName()
                : "FREE";
        Plan plan = planService.getPlanByName(targetPlan);

        // 1. Create Tenant
        Tenant tenant = Tenant.builder()
                .companyName(companyName)
                .plan(plan)
                .status(TenantStatus.ACTIVE)
                .build();
        Tenant savedTenant = tenantRepository.save(tenant);

        // 2. Create Initial Subscription (30-day initial period)
        Instant now = Instant.now();
        Instant periodEnd = now.plus(30, ChronoUnit.DAYS);
        Subscription subscription = Subscription.builder()
                .tenant(savedTenant)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(now)
                .currentPeriodEnd(periodEnd)
                .build();
        subscriptionRepository.save(subscription);

        // 3. Create Default Admin User
        String normalizedEmail = request.adminEmail().toLowerCase().trim();
        User adminUser = User.builder()
                .tenant(savedTenant)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.ROLE_ADMIN)
                .build();
        User savedAdmin = userRepository.save(adminUser);

        // 4. Record Audit Log
        auditService.log(
                savedTenant.getId(),
                savedAdmin.getId(),
                "TENANT_REGISTERED",
                "TENANT",
                savedTenant.getId(),
                String.format("{\"companyName\":\"%s\",\"plan\":\"%s\"}", savedTenant.getCompanyName(), plan.getName())
        );

        // 5. Generate and return JWT
        String token = jwtService.generateToken(
                savedAdmin.getId(),
                savedTenant.getId(),
                savedAdmin.getEmail(),
                savedAdmin.getRole().name()
        );

        return AuthResponse.bearer(
                token,
                savedAdmin.getId(),
                savedTenant.getId(),
                savedAdmin.getEmail(),
                savedAdmin.getRole().name(),
                savedTenant.getCompanyName(),
                plan.getName()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (user.getTenant().getStatus() != TenantStatus.ACTIVE) {
            throw new InvalidCredentialsException("Tenant organization is suspended or cancelled");
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getTenant().getId(),
                user.getEmail(),
                user.getRole().name()
        );

        String planName = user.getTenant().getPlan() != null
                ? user.getTenant().getPlan().getName()
                : "NONE";

        return AuthResponse.bearer(
                token,
                user.getId(),
                user.getTenant().getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getTenant().getCompanyName(),
                planName
        );
    }
}
