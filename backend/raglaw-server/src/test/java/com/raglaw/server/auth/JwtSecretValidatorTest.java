package com.raglaw.server.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtSecretValidatorTest {

    @Test
    void skipsValidationOutsideProdProfile() {
        JwtSecretValidator validator = new JwtSecretValidator(new MockEnvironment());
        setSecret(validator, "raglaw-dev-secret-change-me-32chars-min");
        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void rejectsPlaceholderInProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        JwtSecretValidator validator = new JwtSecretValidator(env);
        setSecret(validator, "raglaw-dev-secret-change-me-32chars-min");
        assertThrows(IllegalStateException.class, () -> validator.run(null));
    }

    @Test
    void rejectsShortSecretInProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        JwtSecretValidator validator = new JwtSecretValidator(env);
        setSecret(validator, "too-short");
        assertThrows(IllegalStateException.class, () -> validator.run(null));
    }

    @Test
    void acceptsStrongSecretInProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        JwtSecretValidator validator = new JwtSecretValidator(env);
        setSecret(validator, "production-secret-with-enough-entropy-32b");
        assertDoesNotThrow(() -> validator.run(null));
    }

    private static void setSecret(JwtSecretValidator validator, String value) {
        try {
            var field = JwtSecretValidator.class.getDeclaredField("secret");
            field.setAccessible(true);
            field.set(validator, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
