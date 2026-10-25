package com.enterprise.billing.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretValidatorTest {

    @Test
    @DisplayName("Throws IllegalStateException when default JWT secret is used in prod profile")
    void shouldRejectDefaultSecretInProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtSecretValidator(JwtSecretValidator.DEFAULT_SECRET, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FATAL: Default JWT secret must not be used in production!");
    }

    @Test
    @DisplayName("Throws IllegalStateException when default JWT secret is used in production profile")
    void shouldRejectDefaultSecretInProductionProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");

        assertThatThrownBy(() -> new JwtSecretValidator(JwtSecretValidator.DEFAULT_SECRET, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FATAL: Default JWT secret must not be used in production!");
    }

    @Test
    @DisplayName("Allows default JWT secret in test or dev profiles")
    void shouldAllowDefaultSecretInTestProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        assertThatCode(() -> new JwtSecretValidator(JwtSecretValidator.DEFAULT_SECRET, env))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Allows custom strong JWT secret in prod profile")
    void shouldAllowCustomSecretInProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        String customSecret = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";

        assertThatCode(() -> new JwtSecretValidator(customSecret, env))
                .doesNotThrowAnyException();
    }
}
