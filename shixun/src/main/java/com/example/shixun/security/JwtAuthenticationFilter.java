package com.example.shixun.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
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
    /** Server-side marker consumed by controllers that need the authenticated principal. */
    public static final String AUTHENTICATED_CLAIMS_ATTRIBUTE = "com.example.shixun.security.JwtAuthenticationFilter.claims";

    private static final Set<String> BACK_OFFICE_ROLES = Set.of("admin", "technician", "feeder");

    private final JwtService jwtService;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper mapper, JdbcTemplate jdbc) {
        this.jwtService = jwtService;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        if (!path.startsWith("/api/")) return true;
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if ("/api/users/login".equals(path)) return true;
        // An unauthenticated POST is the public registration flow. If a Bearer
        // token is supplied, it must be verified and the controller decides from
        // the server-side claims whether the caller may create a staff account.
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/users".equals(path) && !hasBearerAuthorization(request)) return true;
        // These providers authenticate their callbacks with their own signed
        // payloads. Do not broaden this exemption to arbitrary "callback" URLs.
        return path.equals("/api/payments/wechat/notify")
                || path.equals("/api/payments/wechat/refund-notify")
                || path.equals("/api/logistics/callback/kuaidi100");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
        String requestPath = path(request);
        Long readableAssetId = assetReadIdForRequest(requestPath, request.getMethod());
        Long materialLabUploadAssetId = materialLabUploadAssetIdForRequest(requestPath, request.getMethod());
        // Browser model/image viewers cannot set Authorization headers.  A
        // scoped token may therefore appear only on a private asset read URL,
        // or on the single material-variant upload URL.  Do not generalize
        // this branch to arbitrary API paths.
        boolean scopedAssetToken = (token == null || token.isBlank())
                && (readableAssetId != null || materialLabUploadAssetId != null);
        if (scopedAssetToken) {
            token = request.getParameter("access_token");
        }
        if (token == null || token.isBlank()) { unauthorized(response, "请先登录"); return; }
        JwtService.Claims claims;
        try {
            if (materialLabUploadAssetId != null && scopedAssetToken) {
                // State-changing access must use the narrower material-lab
                // scope; an ordinary asset:read token is never enough.
                claims = jwtService.verifyMaterialLabAccessToken(token.trim(), materialLabUploadAssetId);
            } else if (readableAssetId != null && scopedAssetToken) {
                // Existing asset:read preview URLs remain valid.  A
                // material-lab token can also read the model it is editing.
                claims = jwtService.verifyAssetReadOrMaterialLabAccessToken(token.trim(), readableAssetId);
            } else {
                // verify() rejects every scoped asset token, preventing a
                // material-lab token from becoming a general-purpose JWT.
                claims = jwtService.verify(token.trim());
            }
        }
        catch (IllegalArgumentException e) { unauthorized(response, e.getMessage()); return; }
        // JWTs are deliberately stateless, but account roles and status can be
        // changed by an administrator.  Check the canonical row on every API
        // request so an old admin/manager token cannot retain its privileges
        // after a downgrade, rename, or account deletion.  Fail closed if the
        // identity table cannot be read.
        if (!persistedIdentityMatches(claims)) {
            unauthorized(response, "登录身份已失效，请重新登录");
            return;
        }
        if (requiresBackOfficeRole(requestPath, request.getMethod()) && !BACK_OFFICE_ROLES.contains(claims.role())) {
            forbidden(response, "当前账号无权访问管理端资源");
            return;
        }
        request.setAttribute(AUTHENTICATED_CLAIMS_ATTRIBUTE, claims);
        Map<String, String> authHeaders = Map.of(
                "X-Current-User-Id", String.valueOf(claims.userId()),
                "X-Current-User", claims.username(),
                "X-Current-Role", claims.role());
        chain.doFilter(new AuthenticatedRequest(request, authHeaders), response);
    }

    private boolean persistedIdentityMatches(JwtService.Claims claims) {
        if (claims == null || claims.userId() == null || claims.username() == null || claims.role() == null) return false;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT username, role FROM user WHERE id=? LIMIT 1", claims.userId());
            if (rows.isEmpty()) return false;
            Map<String, Object> row = rows.get(0);
            return claims.username().equals(String.valueOf(row.get("username")))
                    && claims.role().equals(String.valueOf(row.get("role")));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("success", false, "message", message));
    }

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN); response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("success", false, "message", message));
    }

    private boolean hasBearerAuthorization(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    private String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }

    private Long assetReadIdForRequest(String path, String method) {
        if (!"GET".equalsIgnoreCase(method)) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("/api/creative/ai/assets/(\\d+)/(?:model-content|content|preview-content)")
                .matcher(path);
        if (!matcher.matches()) return null;
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long materialLabUploadAssetIdForRequest(String path, String method) {
        if (!"POST".equalsIgnoreCase(method)) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("/api/creative/ai/assets/(\\d+)/material-variants")
                .matcher(path);
        if (!matcher.matches()) return null;
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * These paths expose operational data or mutate back-office state. Consumer
     * creation, payment and creative-AI paths intentionally stay outside this
     * list so a role=user JWT keeps access to its C-end experience.
     */
    private boolean requiresBackOfficeRole(String path, String method) {
        // This is the consumer's own account operation. Authentication still
        // runs above; only the broad /api/users management guard is bypassed.
        if ("/api/users/me/cancellation".equals(path)
                && "POST".equalsIgnoreCase(method)) {
            return false;
        }
        // The marketplace order resource has two deliberately different
        // audiences: consumers may create/read their own orders, while the
        // controller limits staff views to operational roles.  Let the
        // controller make that ownership decision after JWT verification.
        if ("/api/creative/orders".equals(path)
                && ("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method))) {
            return false;
        }
        return path.startsWith("/api/suppliers")
                || path.startsWith("/api/warehouse")
                || path.startsWith("/api/logistics")
                || path.startsWith("/api/production")
                || path.startsWith("/api/workflows")
                || path.startsWith("/api/supply-chain/")
                || path.startsWith("/api/mvp")
                || path.startsWith("/api/scale")
                || path.startsWith("/api/users")
                || path.startsWith("/api/notifications")
                || path.startsWith("/api/creative/assistant")
                || path.startsWith("/api/creative/marketing")
                || path.startsWith("/api/creative/dashboard")
                || path.startsWith("/api/creative/orders")
                || path.startsWith("/api/customer-service/admin/")
                || path.startsWith("/api/payments/admin/")
                || path.startsWith("/api/analytics/historical-sales")
                || path.startsWith("/api/creative/ai/consumer-credits/admin/")
                || path.startsWith("/api/creative/ai/consumer-production/admin/")
                || path.startsWith("/api/creative/ai/consumer-assets/review")
                || path.startsWith("/api/creative/ai/consumer-assets/inventory")
                || path.matches("/api/creative/ai/consumer-assets/\\d+/review");
    }

    private static class AuthenticatedRequest extends HttpServletRequestWrapper {
        private final Map<String, String> headers;
        AuthenticatedRequest(HttpServletRequest request, Map<String, String> headers) { super(request); this.headers = headers; }
        @Override public String getHeader(String name) { String matched = headers.get(name); return matched != null ? matched : super.getHeader(name); }
        @Override public Enumeration<String> getHeaders(String name) { String value = headers.get(name); return value != null ? Collections.enumeration(List.of(value)) : super.getHeaders(name); }
        @Override public Enumeration<String> getHeaderNames() { Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames())); names.addAll(headers.keySet()); return Collections.enumeration(names); }
    }
}
