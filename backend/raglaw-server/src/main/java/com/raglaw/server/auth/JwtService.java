package com.raglaw.server.auth;

import com.raglaw.server.config.JwtProperties;
import com.raglaw.server.domain.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UserEntity user) {
        return createToken(user, properties.accessTtlMinutes() * 60);
    }

    public String createRefreshToken(UserEntity user) {
        return createToken(user, properties.refreshTtlDays() * 24 * 60 * 60);
    }

    private String createToken(UserEntity user, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId())
                .claims(Map.of(
                        "email", user.getEmail(),
                        "role", user.getRole(),
                        "name", user.getDisplayName()
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
