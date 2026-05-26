package com.polling.platform.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    // Requests per minute per client IP
    private static final int AUTH_RPM    = 10;
    private static final int VOTE_RPM    = 20;
    private static final int DEFAULT_RPM = 200;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/ws", "/actuator", "/swagger-ui", "/v3/api-docs", "/favicon"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) return true;
        // SSE connections are long-lived — don't count them against the rate limit
        return path.matches("/api/polls/[^/]+/stream");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip     = resolveClientIp(request);
        String path   = request.getRequestURI();
        String method = request.getMethod();
        int limit     = resolveLimit(path, method);
        String key    = "rl:" + ip + ":" + resolveBucket(path, method);

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, WINDOW);
            }

            long used      = count == null ? 0L : count;
            long remaining = Math.max(0L, limit - used);

            response.addHeader("X-RateLimit-Limit",     String.valueOf(limit));
            response.addHeader("X-RateLimit-Remaining", String.valueOf(remaining));

            if (used > limit) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.addHeader("Retry-After", "60");
                response.getWriter().write(
                        "{\"status\":429,\"message\":\"Rate limit exceeded. Retry after 60 seconds.\"," +
                        "\"timestamp\":\"" + LocalDateTime.now() + "\"}"
                );
                log.warn("Rate limit exceeded: ip={} path={} count={}/{}", ip, path, used, limit);
                return;
            }
        } catch (Exception e) {
            // Fail-open: a Redis outage should not block legitimate requests
            log.warn("Rate limiter unavailable, request allowed through: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private int resolveLimit(String path, String method) {
        if (path.startsWith("/api/auth")) return AUTH_RPM;
        if ("POST".equalsIgnoreCase(method) && path.matches("/api/polls/[^/]+/vote")) return VOTE_RPM;
        return DEFAULT_RPM;
    }

    private String resolveBucket(String path, String method) {
        if (path.startsWith("/api/auth")) return "auth";
        if ("POST".equalsIgnoreCase(method) && path.matches("/api/polls/[^/]+/vote")) return "vote";
        return "default";
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xri)) return xri;
        return request.getRemoteAddr();
    }
}
