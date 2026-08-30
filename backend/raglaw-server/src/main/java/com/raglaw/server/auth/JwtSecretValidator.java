package com.raglaw.server.auth;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class JwtSecretValidator implements ApplicationRunner {

    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "raglaw-dev-secret-change-me-32chars-min",
            "change-me-in-production-use-long-random-string"
    );

    @Value("${raglaw.jwt.secret:}")
    private String secret;

    private final Environment environment;

    public JwtSecretValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.matchesProfiles("prod")) {
            return;
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Production requires JWT_SECRET (raglaw.jwt.secret) to be set");
        }
        if (KNOWN_PLACEHOLDERS.contains(secret)) {
            throw new IllegalStateException(
                    "Production JWT secret must not use the default development placeholder");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Production JWT secret must be at least 32 bytes (UTF-8)");
        }
    }
}
