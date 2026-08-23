package com.raglaw.server.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public LoginRateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if ("/api/v1/auth/login".equals(request.getRequestURI())
                && HttpMethod.POST.matches(request.getMethod())) {
            if (!rateLimitService.tryLogin(clientIp(request))) {
                writeRateLimited(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    static void writeRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后再试\"}}");
    }
}
