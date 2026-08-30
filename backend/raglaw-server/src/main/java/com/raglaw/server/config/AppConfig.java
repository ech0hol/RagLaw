package com.raglaw.server.config;

import com.raglaw.server.auth.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, RateLimitProperties.class, CorsProperties.class})
public class AppConfig {
}
