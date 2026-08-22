package com.raglaw.server.auth;

import com.raglaw.server.domain.UserEntity;
import com.raglaw.server.domain.UserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedInitializer.class);
    private static final String ADMIN_EMAIL = "admin@raglaw.local";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeedInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Value("${raglaw.seed.admin-password:}")
    private String configuredAdminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        String password = configuredAdminPassword != null && !configuredAdminPassword.isBlank()
                ? configuredAdminPassword
                : randomPassword();
        UserEntity admin = new UserEntity(
                UUID.randomUUID().toString(),
                ADMIN_EMAIL,
                passwordEncoder.encode(password),
                "系统管理员",
                "ADMIN"
        );
        userRepository.save(admin);
        log.warn("=================================================");
        log.warn("RagLaw seed admin created: {} / {}", ADMIN_EMAIL, password);
        log.warn("=================================================");
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
