package com.enterprise.billing.service;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.domain.Tenant;
import com.enterprise.billing.domain.User;
import com.enterprise.billing.dto.CreateUserRequest;
import com.enterprise.billing.dto.UserResponse;
import com.enterprise.billing.exception.DuplicateResourceException;
import com.enterprise.billing.exception.QuotaExceededException;
import com.enterprise.billing.exception.ResourceNotFoundException;
import com.enterprise.billing.repository.TenantRepository;
import com.enterprise.billing.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        UUID tenantId = getRequiredTenantId();
        return userRepository.findAllByTenantId(tenantId, pageable)
                .map(UserResponse::from);
    }

    public UserResponse createUser(CreateUserRequest request) {
        UUID tenantId = getRequiredTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        // 1. Quota Enforcement: Verify user seat capacity
        long currentUsers = userRepository.countByTenantId(tenantId);
        int maxAllowed = tenant.getPlan() != null ? tenant.getPlan().getMaxUsers() : 5;

        if (currentUsers >= maxAllowed) {
            throw new QuotaExceededException(String.format(
                    "Plan limit of %d users reached (current: %d). Upgrade your subscription tier to invite more seats.",
                    maxAllowed, currentUsers
            ));
        }

        // 2. Uniqueness check within tenant
        String normalizedEmail = request.email().toLowerCase().trim();
        if (userRepository.existsByTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new DuplicateResourceException("User already exists with email: " + normalizedEmail);
        }

        // 3. Persist user with BCrypt 12 hashed password
        User user = User.builder()
                .tenant(tenant)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        User saved = userRepository.save(user);

        // 4. Record audit trail
        auditService.log(
                tenantId,
                null,
                "USER_CREATED",
                "USER",
                saved.getId(),
                String.format("{\"email\":\"%s\",\"role\":\"%s\"}", saved.getEmail(), saved.getRole())
        );

        return UserResponse.from(saved);
    }

    private UUID getRequiredTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResourceNotFoundException("No active tenant context found for operation");
        }
        return tenantId;
    }
}
