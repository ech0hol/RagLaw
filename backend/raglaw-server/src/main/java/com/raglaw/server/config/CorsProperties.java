package com.raglaw.server.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raglaw.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:5173");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            this.allowedOrigins = List.of("http://localhost:5173");
            return;
        }
        if (allowedOrigins.size() == 1 && allowedOrigins.get(0).contains(",")) {
            this.allowedOrigins = Arrays.stream(allowedOrigins.get(0).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            return;
        }
        this.allowedOrigins = allowedOrigins;
    }
}
