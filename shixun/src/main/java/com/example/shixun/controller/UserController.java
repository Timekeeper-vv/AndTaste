package com.example.shixun.controller;

import com.example.shixun.model.User;
import com.example.shixun.service.UserService;
import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
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
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;

    public UserController(UserService userService, JwtService jwtService, JdbcTemplate jdbc) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.jdbc = jdbc;
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
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回用户信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "登录成功"),
        @ApiResponse(responseCode = "400", description = "参数缺失"),
        @ApiResponse(responseCode = "401", description = "用户名或密码错误")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "登录凭证",
        content = @Content(schema = @Schema(example = "{\"username\":\"张三\",\"password\":\"correct-horse-battery-staple\"}"))
    )
    public CompletableFuture<ResponseEntity<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        return userService.login(body.get("username"), body.get("password"))
            .thenApply(user -> {
                if (user == null) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
                }
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
