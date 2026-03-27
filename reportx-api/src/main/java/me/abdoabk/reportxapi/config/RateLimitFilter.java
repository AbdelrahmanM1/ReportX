package me.abdoabk.reportxapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token-based rate limiter.
 *
 * Each JWT (identified by the raw Bearer token string) is allowed at most
 * {@code reportx.ratelimit.requests-per-window} requests within a sliding
 * window of {@code reportx.ratelimit.window-seconds} seconds.
 *
 * If no Authorization header is present the client's IP address is used
 * as the key instead, so unauthenticated endpoints (e.g. /api/auth/exchange)
 * are also protected against brute-force / DoS attacks.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${reportx.ratelimit.requests-per-window:100}")
    private int requestsPerWindow;

    @Value("${reportx.ratelimit.window-seconds:60}")
    private int windowSeconds;

    private static class Bucket {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = Instant.now().getEpochSecond();
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String key = resolveKey(request);
        boolean allowed = isAllowed(key);

        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"Too many requests. Please slow down and try again shortly.\"}"
            );
            return;
        }

        // Pass rate-limit headers downstream so the frontend can display them
        Bucket b = buckets.get(key);
        if (b != null) {
            int remaining = Math.max(0, requestsPerWindow - b.count.get());
            response.setHeader("X-RateLimit-Limit",     String.valueOf(requestsPerWindow));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset",     String.valueOf(b.windowStart + windowSeconds));
        }

        chain.doFilter(request, response);
    }

    private boolean isAllowed(String key) {
        long now = Instant.now().getEpochSecond();

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());

        // Reset window if expired
        synchronized (bucket) {
            if (now - bucket.windowStart >= windowSeconds) {
                bucket.count.set(0);
                bucket.windowStart = now;
            }
        }

        int current = bucket.count.incrementAndGet();
        return current <= requestsPerWindow;
    }

    private String resolveKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Use token as key so each staff member has their own bucket
            return "token:" + authHeader.substring(7);
        }
        // Fall back to IP for unauthenticated requests
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
