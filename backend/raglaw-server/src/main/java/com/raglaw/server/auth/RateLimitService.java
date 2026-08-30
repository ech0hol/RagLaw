package com.raglaw.server.auth;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimitProperties rateLimitProperties;

    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    public boolean tryLogin(String clientKey) {
        return tryAcquire("login:" + clientKey, rateLimitProperties.getLoginPerMinute());
    }

    public boolean tryAguiRun(String clientKey) {
        return tryAcquire("agui:" + clientKey, rateLimitProperties.getAguiPerMinute());
    }

    private boolean tryAcquire(String key, int limit) {
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW.toMillis();
        Deque<Long> timestamps = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
