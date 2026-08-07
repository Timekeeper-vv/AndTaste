package com.example.shixun.controller;

import com.example.shixun.model.User;
import com.example.shixun.service.UserService;
import com.example.shixun.service.EmailVerificationService;
import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户的增删改查及登录接口")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private static final Set<String> SUPPORTED_ROLES = Set.of("admin", "technician", "feeder", "designer", "user");
    private static final Set<String> STAFF_ROLES = Set.of("admin", "technician", "feeder");
    private static final Set<String> USER_MANAGER_ROLES = Set.of("admin", "technician");
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final HttpClient wechatHttp = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ConcurrentMap<String, Long> wechatWebStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WebLoginTicket> wechatWebTickets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MiniWebLoginSession> miniWebLoginSessions = new ConcurrentHashMap<>();
    private volatile MiniProgramAccessToken miniProgramAccessToken;

    @Value("${payment.wechat.app-id:}")
    private String wechatAppId;

    @Value("${payment.wechat.mini-app-secret:}")
    private String wechatMiniAppSecret;

    @Value("${payment.wechat.web-app-id:}")
    private String wechatWebAppId;

    @Value("${payment.wechat.web-app-secret:}")
    private String wechatWebAppSecret;

    @Value("${payment.wechat.web-redirect-uri:https://zhijiansk.com/api/users/wechat-web/callback}")
    private String wechatWebRedirectUri;

    @Value("${payment.wechat.web-success-url:https://zhijiansk.com/}")
    private String wechatWebSuccessUrl;

    private static final String WEB_STATE_COOKIE = "smart_pig_wechat_web_state";
    private static final Duration WEB_STATE_TTL = Duration.ofMinutes(5);
    private static final Duration WEB_TICKET_TTL = Duration.ofMinutes(2);
    private static final Duration MINI_WEB_LOGIN_TTL = Duration.ofMinutes(3);

    public UserController(UserService userService, EmailVerificationService emailVerificationService, JwtService jwtService, JdbcTemplate jdbc,
                          ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.userService = userService;
        this.emailVerificationService = emailVerificationService;
        this.jwtService = jwtService;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @GetMapping
    @Operation(summary = "获取用户（传page参数则返回分页结果）")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public Object findAll(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE) JwtService.Claims principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "10") int size) {
        requireManager(principal);
        if (page != null) return userService.findPage(search, page, Math.max(1, Math.min(size, 100)));
        return userService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取用户", description = "根据用户ID查询单个用户")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public CompletableFuture<ResponseEntity<User>> findById(
            @Parameter(description = "用户ID", example = "1") @PathVariable Long id,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE) JwtService.Claims principal) {
        requireManager(principal);
        return userService.findById(id)
            .thenApply(user -> {
                if (user == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                }
                return ResponseEntity.ok(user);
            });
    }

    @PostMapping
    @Operation(summary = "新增用户", description = "未登录请求仅能注册 C 端 user；创建后台账号必须携带超级管理员 JWT。")
    public CompletableFuture<ResponseEntity<User>> create(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @RequestBody Map<String, Object> body) {
        User user = new User();
        user.setUsername(text(body.get("username")));
        user.setAge(number(body.get("age")));
        user.setEmail(text(body.get("email")));
        user.setPhone(text(body.get("phone")));
        user.setPassword(text(body.get("password")));
        user.setRole(text(body.get("role")));
        // The role is deliberately derived from the server-side authentication state.
        // X-Current-* request headers and a client-provided role must never grant a role.
        boolean publicRegistration = principal == null;
        if (publicRegistration) {
            requireConsent(body, "agreeDisclaimer", "请先阅读并同意免责声明");
            requireConsent(body, "agreeConfidentiality", "请先阅读并同意保密协议");
            requireConsent(body, "agreeContentPolicy", "请先阅读并同意内容创作规范");
            requireConsent(body, "realNameAcknowledged", "请确认后续作品合作须完成实名认证");
            if (text(body.get("complianceSignature")) == null || text(body.get("complianceSignature")).isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请完成合规电子签署");
            }
            user.setRole("user");
        } else {
            requireAdmin(principal);
            if (user.getRole() == null || user.getRole().isBlank()) user.setRole("feeder");
        }
        validateUser(user);
        validatePassword(user.getPassword());
        return userService.save(user)
            .thenApply(saved -> {
                if (publicRegistration) recordComplianceConsent(saved.getId(), body);
                synchronizePlatformIdentity(saved);
                return ResponseEntity.status(HttpStatus.CREATED).body(saved);
            })
            .exceptionally(ex -> {
                Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                if (cause instanceof IllegalArgumentException) throw new ResponseStatusException(HttpStatus.CONFLICT, cause.getMessage());
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new RuntimeException(cause);
            });
    }

    private void recordComplianceConsent(Long userId, Map<String, Object> body) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_compliance_consent (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, disclaimer_accepted TINYINT NOT NULL, confidentiality_accepted TINYINT NOT NULL, content_policy_accepted TINYINT NOT NULL, real_name_acknowledged TINYINT NOT NULL DEFAULT 0, signature_name VARCHAR(100), policy_version VARCHAR(50) NOT NULL, accepted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        try { jdbc.execute("ALTER TABLE user_compliance_consent ADD COLUMN signature_name VARCHAR(100)"); } catch (Exception ignored) { }
        jdbc.update("INSERT INTO user_compliance_consent (user_id,disclaimer_accepted,confidentiality_accepted,content_policy_accepted,real_name_acknowledged,signature_name,policy_version) VALUES (?,?,?,?,?,?,?)", userId, 1, 1, 1, yes(body.get("realNameAcknowledged")) ? 1 : 0, text(body.get("complianceSignature")), "2026-07-30");
    }

    @PostMapping("/email-verification")
    @Operation(summary = "发送邮箱注册验证码")
    public Map<String, Object> sendEmailRegistrationCode(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        emailVerificationService.sendRegistrationCode(
                text(body == null ? null : body.get("email")),
                request == null ? null : request.getRemoteAddr());
        return Map.of("success", true, "message", "验证码已发送，请检查邮箱");
    }

    @PostMapping("/email-register")
    @Operation(summary = "使用邮箱验证码注册 C 端账号")
    public ResponseEntity<Map<String, Object>> emailRegister(@RequestBody Map<String, Object> body) {
        String email = emailVerificationService.normalizeEmail(text(body == null ? null : body.get("email")));
        requireConsent(body, "agreeDisclaimer", "请先阅读并同意免责声明");
        requireConsent(body, "agreeConfidentiality", "请先阅读并同意保密协议");
        requireConsent(body, "agreeContentPolicy", "请先阅读并同意内容创作规范");
        requireConsent(body, "realNameAcknowledged", "请确认后续作品合作须完成实名认证");
        if (blank(text(body == null ? null : body.get("complianceSignature")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请完成合规电子签署");
        }

        User user = new User();
        user.setUsername(text(body == null ? null : body.get("username")));
        user.setAge(number(body == null ? null : body.get("age")));
        user.setEmail(email);
        user.setPhone(text(body == null ? null : body.get("phone")));
        user.setPassword(text(body == null ? null : body.get("password")));
        user.setRole("user");
        validateEmailRegistration(user);
        validatePassword(user.getPassword());
        if (userService.isEmailRegistered(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已注册，请直接登录");
        }
        // Consume the code only after all other registration fields pass validation.
        emailVerificationService.consumeRegistrationCode(email, text(body == null ? null : body.get("emailCode")));
        try {
            User created = userService.createSocialUser(user);
            recordComplianceConsent(created.getId(), body);
            synchronizePlatformIdentity(created);
            userService.touchLastLogin(created.getId());
            recordLoginAudit(created.getId(), "email_registration", "success", null);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "token", jwtService.issue(created),
                    "tokenType", "Bearer",
                    "expiresIn", jwtService.expiresSeconds(),
                    "user", created));
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage());
        }
    }

    private void validateEmailRegistration(User user) {
        if (blank(user.getUsername())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        if (user.getAge() == null || user.getAge() <= 0 || user.getAge() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "年龄格式不正确");
        }
        if (user.getPhone() != null && !user.getPhone().isBlank()
                && !user.getPhone().matches("^[0-9+()\\-\\s]{6,30}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
        validateRole(user.getRole());
    }

    private void requireConsent(Map<String, Object> body, String key, String message) {
        if (!yes(body.get(key))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private boolean yes(Object value) { return value instanceof Boolean ? (Boolean) value : "true".equalsIgnoreCase(String.valueOf(value)); }
    private String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private Integer number(Object value) { try { return value == null || String.valueOf(value).isBlank() ? null : Integer.valueOf(String.valueOf(value)); } catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "年龄格式错误"); } }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "根据ID更新用户信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public CompletableFuture<ResponseEntity<User>> update(
            @Parameter(description = "用户ID", example = "1") @PathVariable Long id,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE) JwtService.Claims principal,
            @RequestBody User user) {
        requireAdmin(principal);
        validateUser(user);
        validateRole(user.getRole());
        if (user.getPassword() != null && !user.getPassword().isBlank()) validatePassword(user.getPassword());
        return userService.update(id, user)
            .thenApply(updated -> {
                if (updated == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                }
                synchronizePlatformIdentity(updated);
                return ResponseEntity.ok(updated);
            });
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "根据ID删除用户")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public CompletableFuture<ResponseEntity<Void>> delete(
            @Parameter(description = "用户ID", example = "1") @PathVariable Long id,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE) JwtService.Claims principal) {
        requireAdmin(principal);
        return userService.delete(id)
            .thenApply(deleted -> {
                if (!deleted) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                }
                removePlatformIdentity(id);
                return ResponseEntity.<Void>noContent().build();
            });
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置用户密码", description = "仅超级管理员可操作；调用方提供新密码，响应不会回显密码")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> resetPassword(
            @Parameter(description = "用户ID", example = "1") @PathVariable Long id,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE) JwtService.Claims principal,
            @RequestBody(required = false) Map<String, String> body) {
        requireAdmin(principal);
        String newPassword = body == null ? null : body.get("password");
        validatePassword(newPassword);
        final String password = newPassword;
        return userService.resetPassword(id, password)
            .thenApply(user -> {
                if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", user.getId());
                result.put("username", user.getUsername());
                synchronizePlatformIdentity(user);
                result.put("message", "密码已重置");
                return ResponseEntity.ok(result);
            })
            .exceptionally(ex -> {
                Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                if (cause instanceof IllegalArgumentException) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, cause.getMessage());
                }
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new RuntimeException(cause);
            });
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名或邮箱和密码登录，返回用户信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "登录成功"),
        @ApiResponse(responseCode = "400", description = "参数缺失"),
        @ApiResponse(responseCode = "401", description = "用户名或密码错误")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "登录凭证",
        content = @Content(schema = @Schema(example = "{\"username\":\"user@example.com\",\"password\":\"correct-horse-battery-staple\"}"))
    )
    public CompletableFuture<ResponseEntity<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        return userService.login(body.get("username"), body.get("password"))
            .thenApply(user -> {
                if (user == null) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名、邮箱或密码错误");
                }
                userService.touchLastLogin(user.getId());
                recordLoginAudit(user.getId(), "password", "success", null);
                synchronizePlatformIdentity(user);
                String token = jwtService.issue(user);
                return ResponseEntity.ok(Map.of("token", token, "tokenType", "Bearer", "expiresIn", jwtService.expiresSeconds(), "user", user));
            })
            .exceptionally(ex -> {
                Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                if (cause instanceof IllegalArgumentException) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, cause.getMessage());
                }
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new RuntimeException(cause);
            });
    }

    /** Starts the desktop/browser WeChat Open Platform QR login flow. */
    @GetMapping("/wechat-web/start")
    @Operation(summary = "微信网站扫码登录开始", description = "返回微信开放平台网站应用的扫码授权地址")
    public Map<String, String> startWechatWebLogin(HttpServletResponse response) {
        requireWechatWebConfiguration();
        purgeWechatWebArtifacts();
        String state = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID();
        wechatWebStates.put(state, System.currentTimeMillis() + WEB_STATE_TTL.toMillis());
        response.addHeader("Set-Cookie", ResponseCookie.from(WEB_STATE_COOKIE, state)
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/users/wechat-web")
                .maxAge(WEB_STATE_TTL).build().toString());
        String authorizationUrl = "https://open.weixin.qq.com/connect/qrconnect?appid=" + encode(wechatWebAppId)
                + "&redirect_uri=" + encode(wechatWebRedirectUri)
                + "&response_type=code&scope=snsapi_login&state=" + encode(state) + "#wechat_redirect";
        return Map.of("authorizationUrl", authorizationUrl);
    }

    /**
     * Receives the Open Platform callback, exchanges the one-time code on the
     * server, then redirects with a short-lived one-time ticket instead of a
     * login JWT. The browser exchanges that ticket through the API below.
     */
    @GetMapping("/wechat-web/callback")
    @Operation(summary = "微信网站扫码登录回调")
    public ResponseEntity<Void> completeWechatWebCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(value = WEB_STATE_COOKIE, required = false) String stateCookie,
            HttpServletResponse response) {
        if (!blank(error)) {
            clearWechatWebStateCookie(response);
            return redirectWechatWebError("wechat_denied");
        }
        if (blank(code) || code.length() > 512 || blank(state)
                || !state.equals(stateCookie) || !consumeWechatWebState(state)) {
            clearWechatWebStateCookie(response);
            return redirectWechatWebError("wechat_state_invalid");
        }
        try {
            WechatIdentity identity = exchangeWechatWebCode(code);
            String ticket = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID();
            wechatWebTickets.put(ticket, new WebLoginTicket(identity, System.currentTimeMillis() + WEB_TICKET_TTL.toMillis()));
            return redirectWechatWebTicket(ticket);
        } catch (ResponseStatusException failure) {
            log.warn("微信网站登录回调失败 status={} reason={}", failure.getStatus().value(), failure.getReason());
            return redirectWechatWebError("wechat_provider_unavailable");
        } catch (Exception failure) {
            log.warn("微信网站登录回调异常", failure);
            return redirectWechatWebError("wechat_provider_unavailable");
        } finally {
            clearWechatWebStateCookie(response);
        }
    }

    /** Exchanges the callback ticket and optionally completes first-login profile setup. */
    @PostMapping("/wechat-web/exchange")
    @Operation(summary = "微信网站扫码登录换取会话")
    public ResponseEntity<Map<String, Object>> exchangeWechatWebLogin(@RequestBody(required = false) Map<String, Object> body) {
        String ticket = text(body == null ? null : body.get("ticket"));
        WebLoginTicket pending = ticket == null ? null : wechatWebTickets.get(ticket);
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
            if (ticket != null) wechatWebTickets.remove(ticket);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信登录票据已失效，请重新扫码");
        }
        WechatLoginOutcome outcome = loginWithWechatIdentity(wechatWebAppId, pending.identity(), body);
        if (outcome.profileRequired()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "WECHAT_PROFILE_REQUIRED",
                    "message", "首次微信登录请补充账号资料并完成合规确认"));
        }
        wechatWebTickets.remove(ticket);
        return loginResponse(outcome.user());
    }

    /** Creates a mini-program QR code for a browser login without a website-app OAuth credential. */
    @PostMapping("/wechat-mini-web/start")
    @Operation(summary = "小程序扫码网页登录开始")
    public Map<String, String> startMiniProgramWebLogin() throws Exception {
        if (blank(wechatAppId) || blank(wechatMiniAppSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信小程序登录配置尚未完成");
        }
        purgeMiniWebLoginSessions();
        // getwxacodeunlimit limits scene to 32 visible characters.
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        miniWebLoginSessions.put(sessionId, new MiniWebLoginSession(null,
                System.currentTimeMillis() + MINI_WEB_LOGIN_TTL.toMillis()));
        try {
            return Map.of("sessionId", sessionId, "qrCodeDataUrl", createMiniProgramLoginCode(sessionId));
        } catch (Exception failure) {
            miniWebLoginSessions.remove(sessionId);
            throw failure;
        }
    }

    /** Creates a mini-program entry QR code for visitors choosing the mobile experience. */
    @PostMapping("/wechat-mini-entry/start")
    @Operation(summary = "小程序端入口二维码")
    public Map<String, String> startMiniProgramEntry() throws Exception {
        if (blank(wechatAppId) || blank(wechatMiniAppSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信小程序登录配置尚未完成");
        }
        return Map.of("qrCodeDataUrl", createMiniProgramEntryCode());
    }

    /** Browser polls this opaque, short-lived session after the user scans the mini-program code. */
    @GetMapping("/wechat-mini-web/status")
    @Operation(summary = "查询小程序扫码网页登录状态")
    public ResponseEntity<Map<String, Object>> miniProgramWebLoginStatus(@RequestParam("sessionId") String sessionId) {
        MiniWebLoginSession session = requiredMiniWebLoginSession(sessionId);
        if (session.user() == null) return ResponseEntity.ok(Map.of("status", "pending"));
        if (!miniWebLoginSessions.remove(sessionId, session)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "网页登录会话已被使用，请重新扫码");
        }
        Map<String, Object> response = new LinkedHashMap<>(loginResponse(session.user()).getBody());
        response.put("status", "authorized");
        return ResponseEntity.ok(response);
    }

    /** Called only by the scanned mini-program page after a fresh uni.login code exchange. */
    @PostMapping("/wechat-mini-web/confirm")
    @Operation(summary = "确认小程序扫码网页登录")
    public ResponseEntity<Map<String, Object>> confirmMiniProgramWebLogin(@RequestBody(required = false) Map<String, Object> body) throws Exception {
        String sessionId = text(body == null ? null : body.get("sessionId"));
        requiredMiniWebLoginSession(sessionId);
        String code = text(body == null ? null : body.get("code"));
        if (code == null || code.length() < 6 || code.length() > 512 || code.contains(" ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信登录凭证无效，请重新扫码");
        }
        ResponseEntity<Map<String, Object>> result = completeWechatIdentityLogin(wechatAppId, exchangeWechatCode(code), null);
        if (!result.getStatusCode().is2xxSuccessful()) return result;
        User user = (User) result.getBody().get("user");
        authorizeMiniWebLoginSession(sessionId, user);
        Map<String, Object> response = new LinkedHashMap<>(loginResponse(user).getBody());
        response.put("status", "authorized");
        response.put("message", "网页已登录");
        return ResponseEntity.ok(response);
    }

    /**
     * Logs a mini-program user in with a one-time uni.login code. The code is
     * exchanged server-side; neither the AppSecret, session_key, nor OpenID
     * ever reaches the mini-program.
     *
     * <p>An existing OpenID binding logs in immediately. A first-time user
     * receives a conflict response asking the client for the required account
     * profile and consent fields, then submits a fresh login code to create the
     * account and binding atomically.</p>
     */
    @PostMapping("/wechat-login")
    @Operation(summary = "微信小程序登录", description = "使用小程序 uni.login 临时凭证登录或创建 C 端账号")
    public ResponseEntity<Map<String, Object>> wechatLogin(@RequestBody(required = false) Map<String, Object> body) throws Exception {
        String code = text(body == null ? null : body.get("code"));
        if (code == null || code.length() < 6 || code.length() > 512 || code.contains(" ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信登录凭证无效，请重新点击微信登录");
        }
        WechatIdentity identity = exchangeWechatCode(code);
        ResponseEntity<Map<String, Object>> result = completeWechatIdentityLogin(wechatAppId, identity, body);
        if (result.getStatusCode().is2xxSuccessful()) {
            String miniWebLoginSession = text(body == null ? null : body.get("miniWebLoginSession"));
            if (miniWebLoginSession != null) {
                authorizeMiniWebLoginSession(miniWebLoginSession, (User) result.getBody().get("user"));
            }
        }
        return result;
    }

    /**
     * Completes first-time mini-program login with WeChat's official phone
     * authorization code. The phone number is never accepted from the client.
     */
    @PostMapping("/wechat-phone-login")
    @Operation(summary = "微信授权手机号登录")
    public ResponseEntity<Map<String, Object>> wechatPhoneLogin(@RequestBody(required = false) Map<String, Object> body) throws Exception {
        String loginCode = text(body == null ? null : body.get("loginCode"));
        String phoneCode = text(body == null ? null : body.get("phoneCode"));
        if (!validWechatCode(loginCode) || !validWechatCode(phoneCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信授权凭证无效，请重新授权手机号");
        }
        WechatIdentity identity = exchangeWechatCode(loginCode);
        String phone = exchangeWechatPhoneCode(phoneCode);
        ResponseEntity<Map<String, Object>> result = completeWechatPhoneLogin(identity, phone, body);
        if (result.getStatusCode().is2xxSuccessful()) {
            String miniWebLoginSession = text(body == null ? null : body.get("miniWebLoginSession"));
            if (miniWebLoginSession != null) {
                authorizeMiniWebLoginSession(miniWebLoginSession, (User) result.getBody().get("user"));
            }
        }
        return result;
    }

    private boolean hasWechatProfile(Map<String, Object> body) {
        return body != null
                && !blank(text(body.get("username")))
                && !blank(text(body.get("phone")))
                && !blank(text(body.get("email")))
                && body.get("age") != null
                && !blank(text(body.get("signature")))
                && yes(body.get("agreeDisclaimer"))
                && yes(body.get("agreeConfidentiality"))
                && yes(body.get("agreeContentPolicy"))
                && yes(body.get("realNameAcknowledged"));
    }

    private ResponseEntity<Map<String, Object>> completeWechatIdentityLogin(
            String appId, WechatIdentity identity, Map<String, Object> body) {
        ensureWechatBindingTable();
        WechatLoginOutcome outcome;
        try {
            outcome = loginWithWechatIdentity(appId, identity, body);
        } catch (DuplicateKeyException collision) {
            // Another request may have completed the same provider binding while
            // this request was creating the account. Reuse that binding instead
            // of creating a second platform user.
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT u.id,u.username,u.age,u.email,u.phone,u.role,u.status "
                            + "FROM wechat_user_binding b JOIN user u ON u.id=b.user_id "
                            + "WHERE b.app_id=? AND b.openid=? LIMIT 1",
                    appId, identity.openId());
            if (rows.isEmpty()) throw collision;
            outcome = new WechatLoginOutcome(userFromRow(rows.get(0)), false);
        }
        if (outcome.profileRequired()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "WECHAT_PROFILE_REQUIRED",
                    "message", "首次微信登录请补充账号资料并完成合规确认"));
        }
        return loginResponse(outcome.user());
    }

    private WechatLoginOutcome loginWithWechatIdentity(
            String appId, WechatIdentity identity, Map<String, Object> body) {
        return transactions.execute(status -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT u.id,u.username,u.age,u.email,u.phone,u.role,u.status "
                            + "FROM wechat_user_binding b JOIN user u ON u.id=b.user_id "
                            + "WHERE b.app_id=? AND b.openid=? LIMIT 1",
                    appId, identity.openId());
            if (!rows.isEmpty()) {
                User existing = userFromRow(rows.get(0));
                if (!"user".equals(existing.getRole())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该微信账号不能登录用户端");
                }
                return new WechatLoginOutcome(existing, false);
            }

            if (!hasWechatProfile(body)) return new WechatLoginOutcome(null, true);
            User created = createWechatUser(body);
            userService.createSocialUser(created);
            recordComplianceConsent(created.getId(), body);
            jdbc.update("INSERT INTO wechat_user_binding(user_id,app_id,openid) VALUES (?,?,?)",
                    created.getId(), appId, identity.openId());
            synchronizePlatformIdentity(created);
            return new WechatLoginOutcome(created, false);
        });
    }

    private ResponseEntity<Map<String, Object>> completeWechatPhoneLogin(
            WechatIdentity identity, String phone, Map<String, Object> body) {
        ensureWechatBindingTable();
        WechatLoginOutcome outcome;
        try {
            outcome = loginWithWechatPhoneIdentity(identity, phone, body);
        } catch (DuplicateKeyException collision) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT u.id,u.username,u.age,u.email,u.phone,u.role,u.status "
                            + "FROM wechat_user_binding b JOIN user u ON u.id=b.user_id "
                            + "WHERE b.app_id=? AND b.openid=? LIMIT 1",
                    identity.appId(), identity.openId());
            if (rows.isEmpty()) throw collision;
            outcome = new WechatLoginOutcome(userFromRow(rows.get(0)), false);
        } catch (IllegalArgumentException collision) {
            // A concurrent first-login request can create the user before its
            // binding transaction commits. Reuse the binding if it is now
            // visible; otherwise report a safe, actionable conflict instead
            // of leaking an internal 500 response.
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT u.id,u.username,u.age,u.email,u.phone,u.role,u.status "
                            + "FROM wechat_user_binding b JOIN user u ON u.id=b.user_id "
                            + "WHERE b.app_id=? AND b.openid=? LIMIT 1",
                    identity.appId(), identity.openId());
            if (rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "账号初始化存在冲突，请稍后重新授权手机号");
            }
            outcome = new WechatLoginOutcome(userFromRow(rows.get(0)), false);
        }
        return loginResponse(outcome.user());
    }

    private WechatLoginOutcome loginWithWechatPhoneIdentity(
            WechatIdentity identity, String phone, Map<String, Object> body) {
        return transactions.execute(status -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT u.id,u.username,u.age,u.email,u.phone,u.role,u.status "
                            + "FROM wechat_user_binding b JOIN user u ON u.id=b.user_id "
                            + "WHERE b.app_id=? AND b.openid=? LIMIT 1",
                    identity.appId(), identity.openId());
            if (!rows.isEmpty()) {
                User existing = userFromRow(rows.get(0));
                if (!"user".equals(existing.getRole())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该微信账号不能登录用户端");
                }
                jdbc.update("UPDATE user SET phone=? WHERE id=?", phone, existing.getId());
                existing.setPhone(phone);
                return new WechatLoginOutcome(existing, false);
            }

            if (!yes(body == null ? null : body.get("agreeTerms"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先同意用户服务与隐私说明");
            }
            User existingPhoneUser = linkExistingUserByAuthorizedPhone(identity, phone);
            if (existingPhoneUser != null) return new WechatLoginOutcome(existingPhoneUser, false);
            User created = createWechatPhoneUser(identity, phone);
            User interruptedRegistration = recoverInterruptedWechatPhoneRegistration(created, identity, phone);
            if (interruptedRegistration != null) return new WechatLoginOutcome(interruptedRegistration, false);
            userService.createSocialUser(created);
            recordComplianceConsent(created.getId(), phoneLoginConsent());
            jdbc.update("INSERT INTO wechat_user_binding(user_id,app_id,openid) VALUES (?,?,?)",
                    created.getId(), identity.appId(), identity.openId());
            synchronizePlatformIdentity(created);
            return new WechatLoginOutcome(created, false);
        });
    }

    /**
     * An existing C-end account can opt into phone quick login by authorizing
     * the same phone number. The phone comes only from the provider API, never
     * from client input, and is required to identify exactly one account.
     */
    private User linkExistingUserByAuthorizedPhone(WechatIdentity identity, String phone) {
        List<Map<String, Object>> matches = jdbc.queryForList(
                "SELECT id,username,age,email,phone,role,status FROM user WHERE phone=? FOR UPDATE", phone);
        if (matches.isEmpty()) return null;
        if (matches.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该手机号关联了多个账号，请使用用户名或邮箱登录后联系平台处理");
        }
        User existing = userFromRow(matches.get(0));
        if (!"user".equals(existing.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该账号不能登录用户端");
        }
        if (existing.getStatus() != null && !"active".equalsIgnoreCase(existing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用，请联系客服");
        }
        List<String> boundOpenIds = jdbc.query(
                "SELECT openid FROM wechat_user_binding WHERE user_id=? AND app_id=? FOR UPDATE",
                (rs, rowNum) -> rs.getString(1), existing.getId(), identity.appId());
        if (!boundOpenIds.isEmpty() && !identity.openId().equals(boundOpenIds.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该账号已绑定其他小程序身份，请使用原登录方式或联系客服处理");
        }
        if (boundOpenIds.isEmpty()) {
            ensureAuthorizedWechatBinding(existing.getId(), identity);
            recordComplianceConsent(existing.getId(), phoneLoginConsent());
        }
        synchronizePlatformIdentity(existing);
        return existing;
    }

    /**
     * Adds a provider binding without turning a repeated or concurrent request
     * into a server error. The unique constraints remain the authority: after
     * the no-op insert, always read the owner back and reject account takeover.
     */
    private void ensureAuthorizedWechatBinding(Long userId, WechatIdentity identity) {
        jdbc.update("INSERT INTO wechat_user_binding(user_id,app_id,openid) VALUES (?,?,?) "
                        + "ON DUPLICATE KEY UPDATE id=id",
                userId, identity.appId(), identity.openId());
        List<Long> ownerIds = jdbc.query(
                "SELECT user_id FROM wechat_user_binding WHERE app_id=? AND openid=? LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), identity.appId(), identity.openId());
        if (ownerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该账号已绑定其他小程序身份，请使用原登录方式或联系客服处理");
        }
        if (!userId.equals(ownerIds.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该微信账号已绑定其他平台账号，请使用原账号登录");
        }
    }

    /**
     * A process restart or an older release could leave a generated social
     * username in {@code user} before its OpenID binding was persisted. The
     * username is a 96-bit hash of the OpenID, but we still require the same
     * officially authorized phone number before recovering that row.
     */
    private User recoverInterruptedWechatPhoneRegistration(User candidate, WechatIdentity identity, String phone) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,username,age,email,phone,role,status FROM user WHERE username=? FOR UPDATE",
                candidate.getUsername());
        if (rows.isEmpty()) return null;
        User existing = userFromRow(rows.get(0));
        if (!phone.equals(existing.getPhone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该手机号对应的账号初始化记录异常，请联系平台客服处理");
        }
        if (!"user".equals(existing.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该账号不能登录用户端");
        }
        if (existing.getStatus() != null && !"active".equalsIgnoreCase(existing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用，请联系客服");
        }
        recordComplianceConsent(existing.getId(), phoneLoginConsent());
        jdbc.update("INSERT INTO wechat_user_binding(user_id,app_id,openid) VALUES (?,?,?)",
                existing.getId(), identity.appId(), identity.openId());
        synchronizePlatformIdentity(existing);
        return existing;
    }

    private Map<String, Object> phoneLoginConsent() {
        return Map.of(
                "agreeDisclaimer", true,
                "agreeConfidentiality", true,
                "agreeContentPolicy", true,
                "realNameAcknowledged", true,
                "complianceSignature", "微信手机号授权");
    }

    private ResponseEntity<Map<String, Object>> loginResponse(User user) {
        if (user == null || (user.getStatus() != null && !"active".equalsIgnoreCase(user.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用，请联系客服");
        }
        userService.touchLastLogin(user.getId());
        user.setLastLoginAt(java.time.LocalDateTime.now());
        recordLoginAudit(user.getId(), "wechat", "success", null);
        return ResponseEntity.ok(Map.of(
                "token", jwtService.issue(user),
                "tokenType", "Bearer",
                "expiresIn", jwtService.expiresSeconds(),
                "user", user));
    }

    private void recordLoginAudit(Long userId, String provider, String result, String failureCode) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes == null ? null : attributes.getRequest();
            jdbc.update("INSERT INTO user_login_audit(user_id,provider,result,failure_code,ip_hash,user_agent_hash) VALUES (?,?,?,?,?,?)",
                    userId, provider, result, failureCode,
                    auditHash(request == null ? null : request.getRemoteAddr()),
                    auditHash(request == null ? null : request.getHeader("User-Agent")));
        } catch (RuntimeException ex) {
            log.warn("Unable to record login audit for user {}", userId, ex);
        }
    }

    private String auditHash(String value) {
        if (blank(value)) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(64);
            for (byte b : digest) encoded.append(String.format("%02x", b));
            return encoded.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("服务器缺少 SHA-256 实现", impossible);
        }
    }

    private User createWechatUser(Map<String, Object> body) {
        User user = new User();
        user.setUsername(text(body.get("username")));
        user.setPhone(text(body.get("phone")));
        user.setEmail(text(body.get("email")));
        user.setAge(number(body.get("age")));
        user.setRole("user");
        user.setStatus("active");
        // Social accounts do not expose or use this password. A random value
        // keeps the password column non-null and prevents password guessing.
        user.setPassword(UUID.randomUUID() + UUID.randomUUID().toString());
        validateUser(user);
        if (!validEmail(user.getEmail())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        if (!yes(body.get("agreeDisclaimer")) || !yes(body.get("agreeConfidentiality"))
                || !yes(body.get("agreeContentPolicy")) || !yes(body.get("realNameAcknowledged"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先完成全部使用确认");
        }
        return user;
    }

    private User createWechatPhoneUser(WechatIdentity identity, String phone) {
        String suffix = socialIdentitySuffix(identity.openId());
        User user = new User();
        user.setUsername("wx_" + suffix);
        user.setPhone(phone);
        // Age and email are optional account profile fields. Do not invent
        // personal data merely to satisfy the legacy password-registration form.
        user.setEmail(null);
        user.setAge(null);
        user.setRole("user");
        user.setStatus("active");
        user.setPassword(UUID.randomUUID() + UUID.randomUUID().toString());
        validateWechatPhoneUser(user);
        return user;
    }

    private String socialIdentitySuffix(String openId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(openId.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(24);
            for (int i = 0; i < 12; i++) result.append(String.format("%02x", digest[i]));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("服务器缺少 SHA-256 实现", impossible);
        }
    }

    private boolean validEmail(String value) {
        return value != null && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private void validateWechatPhoneUser(User user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信账号标识无效");
        }
        if (user.getPhone() == null || !user.getPhone().matches("^[0-9+()\\-\\s]{6,30}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信手机号无效");
        }
        validateRole(user.getRole());
    }

    private User userFromRow(Map<String, Object> row) {
        User user = new User();
        user.setId(((Number) row.get("id")).longValue());
        user.setUsername(text(row.get("username")));
        user.setAge(number(row.get("age")));
        user.setEmail(text(row.get("email")));
        user.setPhone(text(row.get("phone")));
        user.setRole(text(row.get("role")));
        user.setStatus(text(row.get("status")));
        return user;
    }

    private void ensureWechatBindingTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS wechat_user_binding ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, app_id VARCHAR(64) NOT NULL, "
                + "openid VARCHAR(128) NOT NULL, bound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                + "UNIQUE KEY uk_wechat_user_app(user_id, app_id), UNIQUE KEY uk_wechat_app_openid(app_id, openid), "
                + "INDEX idx_wechat_binding_user(user_id))");
    }

    private WechatIdentity exchangeWechatCode(String code) throws Exception {
        if (blank(wechatAppId) || blank(wechatMiniAppSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信小程序登录配置尚未完成");
        }
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + encode(wechatAppId)
                + "&secret=" + encode(wechatMiniAppSecret) + "&js_code=" + encode(code)
                + "&grant_type=authorization_code";
        HttpResponse<String> response;
        try {
            response = wechatHttp.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信登录服务暂时不可用，请稍后重试");
        }
        if (response.statusCode() != 200) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信登录服务暂时不可用，请稍后重试");
        }
        JsonNode root = mapper.readTree(response.body());
        int errorCode = root.path("errcode").asInt(0);
        if (errorCode != 0) {
            if (errorCode == 40029 || errorCode == 40163) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信登录凭证已失效，请重新点击微信登录");
            }
            log.warn("微信小程序登录换取会话失败 errcode={}", errorCode);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信登录校验失败，请稍后重试");
        }
        String openId = root.path("openid").asText("").trim();
        if (openId.isEmpty() || openId.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信登录响应无效，请稍后重试");
        }
        return new WechatIdentity(wechatAppId, openId);
    }

    private WechatIdentity exchangeWechatWebCode(String code) throws Exception {
        requireWechatWebConfiguration();
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=" + encode(wechatWebAppId)
                + "&secret=" + encode(wechatWebAppSecret) + "&code=" + encode(code)
                + "&grant_type=authorization_code";
        HttpResponse<String> response;
        try {
            response = wechatHttp.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信网站登录服务暂时不可用");
        }
        if (response.statusCode() != 200) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信网站登录服务暂时不可用");
        }
        JsonNode root = mapper.readTree(response.body());
        int errorCode = root.path("errcode").asInt(0);
        if (errorCode != 0) {
            log.warn("微信网站登录换取会话失败 errcode={}", errorCode);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信授权已失效，请重新扫码");
        }
        String openId = root.path("openid").asText("").trim();
        if (openId.isEmpty() || openId.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信网站登录响应无效");
        }
        return new WechatIdentity(wechatWebAppId, openId);
    }

    private String exchangeWechatPhoneCode(String code) throws Exception {
        String accessToken = miniProgramAccessToken();
        String body = mapper.writeValueAsString(Map.of("code", code));
        HttpResponse<String> response;
        try {
            response = wechatHttp.send(HttpRequest.newBuilder(URI.create(
                    "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + encode(accessToken)))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信手机号服务暂时不可用，请稍后重试");
        }
        if (response.statusCode() != 200) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信手机号服务暂时不可用，请稍后重试");
        }
        JsonNode root = mapper.readTree(response.body());
        int errorCode = root.path("errcode").asInt(0);
        if (errorCode != 0) {
            log.warn("微信手机号授权换取失败 errcode={}", errorCode);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信手机号授权已失效，请重新点击授权");
        }
        JsonNode phoneInfo = root.path("phone_info");
        String phone = phoneInfo.path("purePhoneNumber").asText("").trim();
        if (phone.isEmpty()) phone = phoneInfo.path("phoneNumber").asText("").trim();
        if (!phone.matches("^[0-9+()\\-\\s]{6,30}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信未返回有效手机号，请重新授权");
        }
        return phone;
    }

    private boolean validWechatCode(String code) {
        return code != null && code.length() >= 6 && code.length() <= 512 && !code.contains(" ");
    }

    private String createMiniProgramLoginCode(String sessionId) throws Exception {
        String accessToken = miniProgramAccessToken();
        String body = mapper.writeValueAsString(Map.of(
                "scene", sessionId,
                "page", "pages/web-login/index",
                "check_path", false));
        HttpResponse<byte[]> response;
        try {
            response = wechatHttp.send(HttpRequest.newBuilder(URI.create(
                    "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + encode(accessToken)))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "生成小程序登录码失败，请稍后重试");
        }
        if (response.statusCode() != 200 || response.body().length == 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "生成小程序登录码失败，请稍后重试");
        }
        if (response.body()[0] == '{') {
            JsonNode error = mapper.readTree(response.body());
            log.warn("生成小程序网页登录码失败 errcode={}", error.path("errcode").asInt());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "生成小程序登录码失败，请检查小程序配置");
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(response.body());
    }

    private String createMiniProgramEntryCode() throws Exception {
        String accessToken = miniProgramAccessToken();
        String body = mapper.writeValueAsString(Map.of(
                "scene", "mobile-entry",
                "page", "pages/login/index",
                "check_path", false));
        HttpResponse<byte[]> response;
        try {
            response = wechatHttp.send(HttpRequest.newBuilder(URI.create(
                    "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + encode(accessToken)))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "生成小程序入口码失败，请稍后重试");
        }
        if (response.statusCode() != 200 || response.body().length == 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "生成小程序入口码失败，请稍后重试");
        }
        if (response.body()[0] == '{') {
            JsonNode error = mapper.readTree(response.body());
            log.warn("生成小程序入口码失败 errcode={}", error.path("errcode").asInt());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "生成小程序入口码失败，请检查小程序配置");
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(response.body());
    }

    private String miniProgramAccessToken() throws Exception {
        MiniProgramAccessToken cached = miniProgramAccessToken;
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now + Duration.ofMinutes(1).toMillis()) return cached.value();
        synchronized (this) {
            cached = miniProgramAccessToken;
            if (cached != null && cached.expiresAt() > now + Duration.ofMinutes(1).toMillis()) return cached.value();
            String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                    + encode(wechatAppId) + "&secret=" + encode(wechatMiniAppSecret);
            HttpResponse<String> response;
            try {
                response = wechatHttp.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (Exception failure) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信小程序服务暂时不可用");
            }
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信小程序服务暂时不可用");
            }
            JsonNode root = mapper.readTree(response.body());
            String value = root.path("access_token").asText("").trim();
            int expiresIn = root.path("expires_in").asInt(0);
            if (value.isEmpty() || expiresIn <= 60) {
                log.warn("获取小程序全局访问令牌失败 errcode={}", root.path("errcode").asInt());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信小程序配置校验失败");
            }
            miniProgramAccessToken = new MiniProgramAccessToken(value, now + Duration.ofSeconds(expiresIn).toMillis());
            return value;
        }
    }

    private MiniWebLoginSession requiredMiniWebLoginSession(String sessionId) {
        if (blank(sessionId) || sessionId.length() != 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "网页登录会话无效，请重新扫码");
        }
        MiniWebLoginSession session = miniWebLoginSessions.get(sessionId);
        if (session == null || session.expiresAt() < System.currentTimeMillis()) {
            if (session != null) miniWebLoginSessions.remove(sessionId, session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "网页登录会话已失效，请重新扫码");
        }
        return session;
    }

    private void authorizeMiniWebLoginSession(String sessionId, User user) {
        if (user == null || user.getId() == null) return;
        miniWebLoginSessions.computeIfPresent(sessionId, (ignored, current) ->
                current.expiresAt() < System.currentTimeMillis() ? null : new MiniWebLoginSession(user, current.expiresAt()));
    }

    private void purgeMiniWebLoginSessions() {
        long now = System.currentTimeMillis();
        miniWebLoginSessions.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    private void requireWechatWebConfiguration() {
        if (blank(wechatWebAppId) || blank(wechatWebAppSecret) || blank(wechatWebRedirectUri)
                || !wechatWebRedirectUri.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "网页微信登录尚未配置开放平台网站应用");
        }
    }

    private boolean consumeWechatWebState(String state) {
        Long expiresAt = wechatWebStates.remove(state);
        return expiresAt != null && expiresAt >= System.currentTimeMillis();
    }

    private void clearWechatWebStateCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", ResponseCookie.from(WEB_STATE_COOKIE, "")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/users/wechat-web")
                .maxAge(Duration.ZERO).build().toString());
    }

    private void purgeWechatWebArtifacts() {
        long now = System.currentTimeMillis();
        wechatWebStates.entrySet().removeIf(entry -> entry.getValue() < now);
        wechatWebTickets.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    private ResponseEntity<Void> redirectWechatWebTicket(String ticket) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(appendQuery(wechatWebSuccessUrl, "wechat_ticket", ticket)))
                .build();
    }

    private ResponseEntity<Void> redirectWechatWebError(String code) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(appendQuery(wechatWebSuccessUrl, "wechat_error", code)))
                .build();
    }

    private String appendQuery(String base, String key, String value) {
        String cleanBase = blank(base) ? "/" : base.trim();
        return cleanBase + (cleanBase.contains("?") ? "&" : "?") + encode(key) + "=" + encode(value);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private record WechatIdentity(String appId, String openId) { }
    private record WebLoginTicket(WechatIdentity identity, long expiresAt) { }
    private record MiniProgramAccessToken(String value, long expiresAt) { }
    private record MiniWebLoginSession(User user, long expiresAt) { }
    private record WechatLoginOutcome(User user, boolean profileRequired) { }

    private void validateUser(User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must not be blank");
        }
        if (user.getAge() == null || user.getAge() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Age must be greater than 0");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be blank");
        }
        if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (!user.getPhone().trim().matches("^[0-9+()\\-\\s]{6,30}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
        validateRole(user.getRole());
    }

    private void validateRole(String role) {
        if (!SUPPORTED_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的用户角色");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        if (!password.equals(password.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码首尾不能包含空格");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少需要" + MIN_PASSWORD_LENGTH + "个字符");
        }
    }

    private void requireAdmin(JwtService.Claims principal) {
        if (principal == null || !"admin".equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可管理用户账号");
        }
    }

    private void requireManager(JwtService.Claims principal) {
        if (principal == null || !USER_MANAGER_ROLES.contains(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员或审批主管可查看用户账号");
        }
    }

    /**
     * Maps the canonical login account in {@code user} to the marketplace's
     * {@code platform_user}. Both systems keep their own schema for now, so the
     * mapping is the source of truth instead of assuming matching numeric IDs.
     */
    private void synchronizePlatformIdentity(User user) {
        if (user == null || user.getId() == null || !platformUserTableAvailable()) return;
        try {
            ensurePlatformIdentityTable();
            String passwordHash = jdbc.queryForObject("SELECT password FROM user WHERE id=?", String.class, user.getId());
            if (passwordHash == null || passwordHash.isBlank()) return;

            Long platformUserId = mappedPlatformUserId(user.getId());
            if (platformUserId == null) {
                List<Map<String, Object>> matches = jdbc.queryForList(
                        "SELECT id, role FROM platform_user WHERE username=? LIMIT 1", user.getUsername());
                if (!matches.isEmpty()) {
                    Map<String, Object> match = matches.get(0);
                    String existingRole = text(match.get("role"));
                    if (!compatiblePlatformRole(user.getRole(), existingRole)) {
                        log.warn("Skipped marketplace identity mapping for canonical user {} because the username is already bound to an incompatible platform role", user.getId());
                        return;
                    }
                    platformUserId = ((Number) match.get("id")).longValue();
                } else {
                    jdbc.update("INSERT INTO platform_user (username,password,display_name,email,phone,role,status) VALUES (?,?,?,?,?,?,?)",
                            user.getUsername(), passwordHash, user.getUsername(), user.getEmail(), user.getPhone(), platformRole(user.getRole()), "active");
                    platformUserId = jdbc.queryForObject("SELECT id FROM platform_user WHERE username=?", Long.class, user.getUsername());
                }
                jdbc.update("INSERT INTO user_platform_identity (user_id,platform_user_id) VALUES (?,?)", user.getId(), platformUserId);
            }
            jdbc.update("UPDATE platform_user SET username=?,password=?,display_name=?,email=?,phone=?,role=? WHERE id=?",
                    user.getUsername(), passwordHash, user.getUsername(), user.getEmail(), user.getPhone(), platformRole(user.getRole()), platformUserId);
        } catch (Exception ex) {
            // Account authentication must remain available when a legacy database has
            // not yet imported the marketplace schema. The next successful login will retry.
            log.warn("Unable to synchronize marketplace identity for canonical user {}", user.getId(), ex);
        }
    }

    private boolean platformUserTableAvailable() {
        try {
            jdbc.queryForList("SELECT 1 FROM platform_user LIMIT 1");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void ensurePlatformIdentityTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_platform_identity (user_id BIGINT NOT NULL PRIMARY KEY, platform_user_id BIGINT NOT NULL UNIQUE, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
    }

    private Long mappedPlatformUserId(Long userId) {
        List<Long> rows = jdbc.query("SELECT platform_user_id FROM user_platform_identity WHERE user_id=?", (rs, rowNum) -> rs.getLong(1), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void removePlatformIdentity(Long userId) {
        try {
            if (platformUserTableAvailable()) jdbc.update("DELETE FROM user_platform_identity WHERE user_id=?", userId);
        } catch (Exception ex) {
            log.warn("Unable to remove marketplace identity mapping for canonical user {}", userId, ex);
        }
    }

    private boolean compatiblePlatformRole(String canonicalRole, String existingPlatformRole) {
        return platformRole(canonicalRole).equals(existingPlatformRole);
    }

    private String platformRole(String canonicalRole) {
        if ("admin".equals(canonicalRole)) return "admin";
        if ("designer".equals(canonicalRole)) return "designer";
        if (STAFF_ROLES.contains(canonicalRole)) return "operator";
        return "consumer";
    }
}
