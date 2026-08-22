package com.raglaw.server.auth;

import com.raglaw.common.auth.CurrentUserHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                Claims claims = jwtService.parse(header.substring(7));
                UserContext.set(new AuthUser(
                        claims.getSubject(),
                        claims.get("email", String.class),
                        claims.get("name", String.class),
                        claims.get("role", String.class)
                ));
                CurrentUserHolder.set(claims.getSubject());
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            CurrentUserHolder.clear();
        }
    }
}
