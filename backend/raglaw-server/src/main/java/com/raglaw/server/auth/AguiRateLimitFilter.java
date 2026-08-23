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
public class AguiRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public AguiRateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if ("/api/v1/agui/run".equals(request.getRequestURI())
                && HttpMethod.POST.matches(request.getMethod())) {
            String key = resolveKey(request);
            if (!rateLimitService.tryAguiRun(key)) {
                LoginRateLimitFilter.writeRateLimited(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String resolveKey(HttpServletRequest request) {
        AuthUser user = UserContext.get();
        if (user != null && user.id() != null) {
            return "user:" + user.id();
        }
        return "ip:" + LoginRateLimitFilter.clientIp(request);
    }
}
