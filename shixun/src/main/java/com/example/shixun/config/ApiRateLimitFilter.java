package com.example.shixun.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Small single-node guard for public and paid/AI endpoints. A multi-node
 * deployment should move this policy to Redis or the reverse proxy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    @Value("${app.rate-limit.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    @Autowired
    public ApiRateLimitFilter(ObjectMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    ApiRateLimitFilter(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Rule rule = ruleFor(request.getRequestURI(), request.getMethod());
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }
        long now = clock.millis();
        String key = rule.name + "|" + requesterKey(request);
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        boolean allowed;
        synchronized (window) {
            if (now - window.startedAt >= rule.windowMillis) {
                window.startedAt = now;
                window.count = 0;
            }
            allowed = ++window.count <= rule.maxRequests;
        }
        if (!allowed) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(Math.max(1, (rule.windowMillis - (now - window.startedAt)) / 1000)));
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "message", "请求过于频繁，请稍后再试",
                    "requestId", request.getAttribute("requestId") == null ? "unknown" : request.getAttribute("requestId")));
            return;
        }
        chain.doFilter(request, response);
        if (windows.size() > 5000) cleanup(now);
    }

    private Rule ruleFor(String path, String method) {
        if (!"POST".equalsIgnoreCase(method)) return null;
        if ("/api/users/login".equals(path)) return new Rule("password-login", 10, 60_000);
        if ("/api/users/email-verification".equals(path)) return new Rule("email-code", 5, 600_000);
        if ("/api/users/email-register".equals(path)) return new Rule("email-register", 10, 600_000);
        if ("/api/users/wechat-login".equals(path) || "/api/users/wechat-phone-login".equals(path)
                || "/api/users/wechat-web/exchange".equals(path)
                || "/api/users/wechat-mini-web/start".equals(path)
                || "/api/users/wechat-mini-entry/start".equals(path)
                || "/api/users/wechat-mini-web/confirm".equals(path)) {
            return new Rule("wechat-login", 20, 60_000);
        }
        if ("/api/payments/orders".equals(path) || "/api/payments/sample-orders".equals(path)
                || "/api/payments/commercial-quote-sample-orders".equals(path)
                || "/api/payments/commercial-guidance-orders".equals(path)) {
            return new Rule("payment-order", 10, 60_000);
        }
        if (path.matches("/api/creative/ai/conversations/[^/]+/chat")) {
            // A conversational turn may make several local state transitions,
            // and old clients can retry after a network timeout. Isolate the
            // budget per authenticated user instead of sharing one proxy IP.
            return new Rule("creative-chat", 60, 60_000);
        }
        if (path.startsWith("/api/creative/ai/") || "/api/creative/ai".equals(path)) {
            return new Rule("creative-ai", 30, 60_000);
        }
        return null;
    }

    private String requesterKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            if (!token.isEmpty()) return "token:" + sha256(token);
        }
        return "ip:" + clientAddress(request);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法计算请求身份指纹", e);
        }
    }

    private String clientAddress(HttpServletRequest request) {
        // The default uses the socket address and cannot be spoofed. Configure
        // Nginx to overwrite X-Forwarded-For before enabling proxy-aware limits.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (trustForwardedFor && forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private void cleanup(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt > 900_000);
    }

    private static final class Window {
        private long startedAt;
        private int count;
        private Window(long startedAt) { this.startedAt = startedAt; }
    }

    private record Rule(String name, int maxRequests, long windowMillis) { }
}
