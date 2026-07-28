package com.example.shixun.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final ObjectMapper mapper;
    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper mapper) { this.jwtService = jwtService; this.mapper = mapper; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) return true;
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if ("/api/users/login".equals(path)) return true;
        if ("/api/auth/me".equals(path)) return false;
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/users".equals(path)) return true;
        return path.equals("/api/payments/wechat/notify") || path.contains("/callback/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
        // 3D/model viewer and browser download links cannot set Authorization headers. Only authenticated GET media links may use this short-lived JWT query parameter.
        if ((token == null || token.isBlank()) && "GET".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().matches("/api/creative/ai/assets/\\d+/(model-content|content)")) {
            token = request.getParameter("access_token");
        }
        if (token == null || token.isBlank()) { unauthorized(response, "请先登录"); return; }
        JwtService.Claims claims;
        try { claims = jwtService.verify(token.trim()); }
        catch (IllegalArgumentException e) { unauthorized(response, e.getMessage()); return; }
        Map<String, String> authHeaders = Map.of(
                "X-Current-User-Id", String.valueOf(claims.userId()),
                "X-Current-User", claims.username(),
                "X-Current-Role", claims.role());
        chain.doFilter(new AuthenticatedRequest(request, authHeaders), response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("success", false, "message", message));
    }

    private static class AuthenticatedRequest extends HttpServletRequestWrapper {
        private final Map<String, String> headers;
        AuthenticatedRequest(HttpServletRequest request, Map<String, String> headers) { super(request); this.headers = headers; }
        @Override public String getHeader(String name) { String matched = headers.get(name); return matched != null ? matched : super.getHeader(name); }
        @Override public Enumeration<String> getHeaders(String name) { String value = headers.get(name); return value != null ? Collections.enumeration(List.of(value)) : super.getHeaders(name); }
        @Override public Enumeration<String> getHeaderNames() { Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames())); names.addAll(headers.keySet()); return Collections.enumeration(names); }
    }
}
