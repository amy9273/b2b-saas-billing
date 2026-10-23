# Spec 02 — Multi-Tenant JWT Security & Context Filter

---

## 1. Goal & Objectives

Secure the enterprise billing platform by implementing a robust, stateless JWT security filter chain that establishes tenant isolation on every HTTP request:
1. Cryptographic JWT creation and parsing using HMAC-SHA256 and JJWT 0.12.x.
2. Tenant claim injection (`tenantId`, `userId`, `role`, `email`) into tokens.
3. Thread-safe `TenantContext` (`ThreadLocal`) to hold the resolved tenant ID for the duration of each HTTP request.
4. `JwtAuthenticationFilter` (`OncePerRequestFilter`) to validate tokens, authenticate the user, populate `TenantContext`, and guarantee thread cleanup in a `finally` block (Invariant #2).
5. RFC 7807 Problem Details compliant error responses for `401 Unauthorized` and `403 Forbidden` security failures.

---

## 2. Requirements & Deliverables

### Deliverable 1: `TenantContext`
- Class: `com.enterprise.billing.context.TenantContext`
- Methods:
  - `public static void setTenantId(UUID tenantId)`
  - `public static UUID getTenantId()`
  - `public static void clear()`
- Invariant: ThreadLocal must be cleared in a `finally` block on every single request. Never allow thread reuse in thread pools to leak tenant context.

### Deliverable 2: `JwtService`
- Class: `com.enterprise.billing.service.JwtService`
- Capabilities:
  - Generate JWTs signed with HMAC-SHA256 containing:
    - `sub` (user email)
    - `tenantId` (UUID string)
    - `userId` (UUID string)
    - `role` (e.g., `ROLE_ADMIN`, `ROLE_MEMBER`)
    - `iat` (issued at), `exp` (expiration timestamp)
  - Extract `tenantId`, `userId`, `role`, and `email` from valid tokens.
  - Validate token signatures and expiration timestamps.
  - Throw explicit domain exceptions on expired or malformed tokens.

### Deliverable 3: `JwtAuthenticationFilter`
- Class: `com.enterprise.billing.config.JwtAuthenticationFilter`
- Extends `OncePerRequestFilter`.
- Intercepts requests bearing `Authorization: Bearer <token>`.
- Extracts claims, loads Spring Security `Authentication` (`UsernamePasswordAuthenticationToken` with GrantedAuthorities based on `role`), sets it in `SecurityContextHolder`.
- Sets `TenantContext.setTenantId(tenantId)`.
- Critical: Executes `TenantContext.clear()` in a `finally` block for every intercepted request.

### Deliverable 4: RFC 7807 Security Handlers
- `JwtAuthenticationEntryPoint`: Returns HTTP 401 with RFC 7807 Problem Details JSON format on unauthenticated access attempts or invalid/expired tokens.
- `CustomAccessDeniedHandler`: Returns HTTP 403 with RFC 7807 Problem Details JSON format when authenticated user lacks required role/authority.

### Deliverable 5: Updated `SecurityConfig`
- Wires `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter.class`.
- Registers `JwtAuthenticationEntryPoint` and `CustomAccessDeniedHandler`.
- Configures URL permissions:
  - Public: `/api/auth/**`, `/actuator/health`, `/error`
  - Authenticated: All other endpoints (`/api/**`)

---

## 3. Verification Criteria
1. `TenantContextTest`: Verifies `ThreadLocal` setting, getting, isolation across threads, and clean clearing.
2. `JwtServiceTest`: Verifies token creation, claim extraction, tamper resistance (signature invalidation), and expiration detection.
3. `JwtSecurityIntegrationTest`:
   - Request with valid JWT returns `200 OK` and echoes the authenticated user and tenant ID.
   - Request to secured endpoint with missing token returns `401 Unauthorized` with RFC 7807 Problem Details JSON.
   - Request to secured endpoint with invalid/malformed token returns `401 Unauthorized` with RFC 7807 Problem Details JSON.
   - Post-request verification guarantees `TenantContext.getTenantId()` is null (no thread pollution).
