package com.raglaw.server.auth;

import com.raglaw.common.auth.CurrentUserHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
                AuthUser user = new AuthUser(
                        claims.getSubject(),
                        claims.get("email", String.class),
                        claims.get("name", String.class),
                        claims.get("role", String.class)
                );
                UserContext.set(user);
                CurrentUserHolder.set(claims.getSubject());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))
                ));
            }
            filterChain.doFilter(request, response);
        } finally {
            if (!request.isAsyncStarted()) {
                UserContext.clear();
                CurrentUserHolder.clear();
                SecurityContextHolder.clearContext();
            }
        }
    }
}
