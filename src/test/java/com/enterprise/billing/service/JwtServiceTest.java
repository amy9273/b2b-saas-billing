package com.enterprise.billing.service;

import com.enterprise.billing.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("JwtService generates valid signed token and extracts claims successfully")
    void shouldGenerateAndExtractClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "admin@acme.com";
        String role = "ROLE_ADMIN";

        String token = jwtService.generateToken(userId, tenantId, email, role);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractTenantId(token)).isEqualTo(tenantId);
        assertThat(jwtService.extractRole(token)).isEqualTo(role);
    }

    @Test
    @DisplayName("JwtService rejects tampered token signatures")
    void shouldRejectTamperedToken() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, tenantId, "user@test.com", "ROLE_MEMBER");

        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
        assertThatThrownBy(() -> jwtService.extractAllClaims(tamperedToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("JwtService rejects expired token")
    void shouldRejectExpiredToken() {
        // Create service with immediate expiration (1 ms)
        JwtService shortLivedJwtService = new JwtService(SECRET, 1);
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = shortLivedJwtService.generateToken(userId, tenantId, "expired@test.com", "ROLE_USER");

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(shortLivedJwtService.isTokenValid(token)).isFalse();
        assertThatThrownBy(() -> shortLivedJwtService.extractAllClaims(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }
}
