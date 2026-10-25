package com.enterprise.billing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class JwtSecretValidator {

    public static final String DEFAULT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    public JwtSecretValidator(
            @Value("${app.jwt.secret}") String secret,
            Environment env
    ) {
        if (secret == null || secret.isBlank() || DEFAULT_SECRET.equals(secret.trim())) {
            if (env.acceptsProfiles(Profiles.of("prod", "production"))) {
                throw new IllegalStateException("FATAL: Default JWT secret must not be used in production!");
            }
        }
    }
}
