package com.enterprise.billing.service;

import com.enterprise.billing.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for creating, signing, parsing, and validating multi-tenant JSON Web Tokens (JJWT 0.12.x).
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT with subject (email) and tenant claims.
     */
    public String generateToken(UUID userId, UUID tenantId, String email, String role) {
        return generateToken(userId, tenantId, email, role, Map.of());
    }

    /**
     * Generates a signed JWT with additional custom claims.
     */
    public String generateToken(UUID userId, UUID tenantId, String email, String role, Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(email)
                .claim("userId", userId.toString())
                .claim("tenantId", tenantId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extracts all claims from a signed JWT. Throws InvalidTokenException if signature is invalid or token is expired.
     */
    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid, expired, or tampered JWT: " + e.getMessage(), e);
        }
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractTenantId(String token) {
        String tenantIdStr = extractAllClaims(token).get("tenantId", String.class);
        if (tenantIdStr == null) {
            throw new InvalidTokenException("JWT is missing required tenantId claim");
        }
        try {
            return UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("JWT tenantId claim is not a valid UUID: " + tenantIdStr, e);
        }
    }

    public UUID extractUserId(String token) {
        String userIdStr = extractAllClaims(token).get("userId", String.class);
        if (userIdStr == null) {
            throw new InvalidTokenException("JWT is missing required userId claim");
        }
        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("JWT userId claim is not a valid UUID: " + userIdStr, e);
        }
    }

    public String extractRole(String token) {
        String role = extractAllClaims(token).get("role", String.class);
        if (role == null) {
            throw new InvalidTokenException("JWT is missing required role claim");
        }
        return role;
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (InvalidTokenException e) {
            return false;
        }
    }
}
