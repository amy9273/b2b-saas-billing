# Spec 11 — Security Hardening, CI Parity & Documentation Sync

---

## 1. Goal & Objectives

Harden access control, eliminate hardcoded secret fallbacks, resolve test skipping, and synchronize documentation:
1. **Invoice Payment Role Authorization**: Add `@PreAuthorize("hasAuthority('ROLE_ADMIN')")` to `POST /api/invoices/{id}/pay` in `InvoiceController`.
2. **Fail-Fast JWT Secret Security**: Prohibit starting the application in `prod`/`production` profiles if `JWT_SECRET` is unset or equals the default fallback secret.
3. **Configure Browser CORS**: Replace `.cors(AbstractHttpConfigurer::disable)` with explicit `CorsConfigurationSource` to support browser SPAs.
4. **Testcontainers Execution Parity & CI Quality Gate**:
   - Provide explicit Maven profiles (`-Ppostgres-it`).
   - Configure CI to enforce 0 skipped tests.
5. **Synchronize Project Documentation**:
   - Update `README.md` to reflect all completed units (01-06) and current test statistics (40 tests, accurate execution time).

---

## 2. In-Depth Root Cause & Failure Mode Analysis

### Root Cause 1: Missing Method Authorization on Sensitive Endpoint
- In `InvoiceController.java:L39`, `payInvoice` has no `@PreAuthorize` annotation.
- Any authenticated user with `ROLE_MEMBER` can trigger payment state changes on invoices.

### Root Cause 2: Hardcoded JWT Fallback Secret
- If deployed without `JWT_SECRET`, the application silently boots using the public default HMAC key.
- Attackers can forge administrative JWTs and compromise all organizations.

### Root Cause 3: Silent Test Skipping Without Docker
- In `PostgresIntegrationTest`, `@Testcontainers(disabledWithoutDocker = true)` silently skips 3 tests when Docker is offline, giving developers false confidence.

---

## 3. Requirements & Technical Deliverables

### Deliverable 1: Access Control on `InvoiceController`
```java
@PostMapping("/{id}/pay")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public ResponseEntity<InvoiceResponse> payInvoice(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(invoiceService.payInvoice(id));
}
```

### Deliverable 2: JWT Startup Secret Validator
```java
@Component
public class JwtSecretValidator {
    public JwtSecretValidator(
            @Value("${app.jwt.secret}") String secret,
            Environment env
    ) {
        if ("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970".equals(secret)) {
            if (env.acceptsProfiles(Profiles.of("prod", "production"))) {
                throw new IllegalStateException("FATAL: Default JWT secret must not be used in production!");
            }
        }
    }
}
```

### Deliverable 3: CORS Configuration in `SecurityConfig`
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://app.enterprise-billing.com"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Idempotency-Key"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

### Deliverable 4: Documentation Drift Remediation in `README.md`
- Update roadmap table (mark Units 05 and 06 as Complete, add Units 07-11 as Remediation Roadmap).
- Update test metrics (40 tests, accurate execution time).

---

## 4. Test & Verification Plan

1. **Payment RBAC Test**:
   - Send `POST /api/invoices/{id}/pay` with `ROLE_MEMBER` token.
   - Assert `403 Forbidden` (`ERR_FORBIDDEN`).
   - Send with `ROLE_ADMIN` token -> Assert `200 OK`.
2. **Production Secret Rejection Test**:
   - Run context boot with `SPRING_PROFILES_ACTIVE=prod` and default secret.
   - Assert `ApplicationContext` fails to start with `IllegalStateException`.
