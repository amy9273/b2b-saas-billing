package com.enterprise.billing.config;

import com.enterprise.billing.context.TenantContext;
import com.enterprise.billing.domain.IdempotencyRecord;
import com.enterprise.billing.domain.IdempotencyStatus;
import com.enterprise.billing.dto.ProblemDetails;
import com.enterprise.billing.service.IdempotencyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Filter that intercepts mutating HTTP requests with idempotency keys, guaranteeing exactly-once
 * execution semantics across the 4-phase lifecycle: PROCESSING, COMPLETED, FAILED, and 24h expiration.
 * Validates request payload fingerprints to prevent malicious or accidental payload alterations with reused keys.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS = Set.of(
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name()
    );

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Only intercept mutating HTTP methods
        if (!MUTATING_METHODS.contains(request.getMethod().toUpperCase())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Only intercept when a tenant context is active
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Buffer request body to allow inspection & downstream controller binding
        CachedBodyHttpServletRequest cachedRequest = (request instanceof CachedBodyHttpServletRequest)
                ? (CachedBodyHttpServletRequest) request
                : new CachedBodyHttpServletRequest(request);

        // 4. Resolve idempotency key (header takes precedence, then JSON body)
        String idempotencyKey = extractIdempotencyKey(cachedRequest, cachedRequest.getCachedBody());
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(cachedRequest, response);
            return;
        }

        // 5. Compute SHA-256 payload fingerprint (HTTP method + URI + raw body)
        String requestHash = computePayloadHash(request.getMethod(), request.getRequestURI(), cachedRequest.getCachedBody());

        // 6. Check existing idempotency record
        Optional<IdempotencyRecord> existingOpt = idempotencyService.findRecord(tenantId, idempotencyKey);
        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();
            boolean isExpired = existing.getExpiresAt().isBefore(Instant.now());

            if (!isExpired) {
                if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                    // Verify payload hash matches
                    if (existing.getRequestHash() != null && !existing.getRequestHash().equals(requestHash)) {
                        writeProblemDetails(
                                response,
                                request.getRequestURI(),
                                HttpServletResponse.SC_CONFLICT,
                                "Idempotency Conflict",
                                "Idempotency key reused with different request payload",
                                "ERR_IDEMPOTENCY_CONFLICT"
                        );
                        return;
                    }

                    // Return cached completed response
                    writeCachedResponse(response, existing);
                    return;
                }

                if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                    writeProblemDetails(
                            response,
                            request.getRequestURI(),
                            HttpServletResponse.SC_CONFLICT,
                            "Operation in Progress",
                            "An operation with this idempotency key is currently being processed",
                            "ERR_IDEMPOTENCY_IN_PROGRESS"
                    );
                    return;
                }
            }
        }

        // 7. Transition to PROCESSING state atomically
        try {
            idempotencyService.startProcessing(tenantId, idempotencyKey, request.getRequestURI(), requestHash);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race condition: another request claimed this idempotency key
            writeProblemDetails(
                    response,
                    request.getRequestURI(),
                    HttpServletResponse.SC_CONFLICT,
                    "Operation in Progress",
                    "An operation with this idempotency key is currently being processed",
                    "ERR_IDEMPOTENCY_IN_PROGRESS"
            );
            return;
        }

        // 8. Execute downstream filter chain inside response wrapper
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cachedRequest, responseWrapper);
            int status = responseWrapper.getStatus();
            if (status < 500) {
                String responseBody = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
                idempotencyService.markCompleted(tenantId, idempotencyKey, status, responseBody);
            } else {
                idempotencyService.markFailedOrDelete(tenantId, idempotencyKey);
            }
        } catch (Exception ex) {
            idempotencyService.markFailedOrDelete(tenantId, idempotencyKey);
            throw ex;
        } finally {
            responseWrapper.copyBodyToResponse();
        }
    }

    private String extractIdempotencyKey(HttpServletRequest request, byte[] bodyBytes) {
        String headerKey = request.getHeader("X-Idempotency-Key");
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey.trim();
        }

        if (bodyBytes != null && bodyBytes.length > 0) {
            String contentType = request.getContentType();
            if (contentType != null && contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE)) {
                try {
                    JsonNode root = objectMapper.readTree(bodyBytes);
                    if (root.hasNonNull("idempotencyKey")) {
                        String bodyKey = root.get("idempotencyKey").asText();
                        if (!bodyKey.isBlank()) {
                            return bodyKey.trim();
                        }
                    }
                } catch (Exception ignored) {
                    // Not a valid JSON payload or non-object JSON; ignore
                }
            }
        }
        return null;
    }

    private String computePayloadHash(String method, String uri, byte[] bodyBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.toUpperCase().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(uri.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            if (bodyBytes != null && bodyBytes.length > 0) {
                digest.update(bodyBytes);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in runtime", e);
        }
    }

    private void writeProblemDetails(
            HttpServletResponse response,
            String uri,
            int status,
            String title,
            String detail,
            String errorCode
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetails problem = ProblemDetails.of(
                "https://api.billing.enterprise.com/errors/conflict",
                title,
                status,
                detail,
                uri,
                errorCode
        );
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private void writeCachedResponse(HttpServletResponse response, IdempotencyRecord record) throws IOException {
        int status = record.getResponseStatus() != null ? record.getResponseStatus() : HttpServletResponse.SC_OK;
        response.setStatus(status);
        if (status >= 400) {
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        } else {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
        if (record.getResponseBody() != null) {
            byte[] bodyBytes = record.getResponseBody().getBytes(StandardCharsets.UTF_8);
            response.getOutputStream().write(bodyBytes);
            response.getOutputStream().flush();
        }
    }
}
