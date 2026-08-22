package com.raglaw.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raglaw.jwt")
public record JwtProperties(
        String secret,
        long accessTtlMinutes,
        long refreshTtlDays
) {
}
