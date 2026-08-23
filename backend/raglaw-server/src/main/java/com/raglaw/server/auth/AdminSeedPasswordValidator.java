package com.raglaw.server.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AdminSeedPasswordValidator implements ApplicationRunner {

    @Value("${raglaw.seed.admin-password:}")
    private String configuredAdminPassword;

    private final Environment environment;

    public AdminSeedPasswordValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.matchesProfiles("prod")) {
            return;
        }
        if (configuredAdminPassword == null || configuredAdminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Production requires RAGLAW_SEED_ADMIN_PASSWORD (raglaw.seed.admin-password) to be set");
        }
    }
}
