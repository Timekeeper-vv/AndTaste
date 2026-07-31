package com.example.shixun.controller;

import com.example.shixun.model.User;
import com.example.shixun.service.UserService;
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

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户的增删改查及登录接口")
public class UserController {

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
            @RequestHeader(value = "X-Current-Role", required = false) String currentRole,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "10") int size) {
        requireManager(currentRole);
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
            @RequestHeader(value = "X-Current-Role", required = false) String currentRole) {
        requireManager(currentRole);
        return userService.findById(id)
            .thenApply(user -> {
                if (user == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                }
                return ResponseEntity.ok(user);
            });
    }

    @PostMapping
    @Operation(summary = "新增用户", description = "创建新用户。前台注册必须确认内容规范、免责声明和保密协议。")
    public CompletableFuture<ResponseEntity<User>> create(
            @RequestHeader(value = "X-Current-Role", required = false) String currentRole,
            @RequestBody Map<String, Object> body) {
        User user = new User();
        user.setUsername(text(body.get("username")));
        user.setAge(number(body.get("age")));
        user.setEmail(text(body.get("email")));
        user.setPhone(text(body.get("phone")));
        user.setPassword(text(body.get("password")));
        user.setRole(text(body.get("role")));
        boolean publicRegistration = currentRole == null || currentRole.isBlank();
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
            requireAdmin(currentRole);
            if (user.getRole() == null || user.getRole().isBlank()) user.setRole("feeder");
        }
        validateUser(user);
        if (user.getPassword() == null || user.getPassword().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        return userService.save(user)
            .thenApply(saved -> {
                if (publicRegistration) recordComplianceConsent(saved.getId(), body);
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
            @RequestHeader(value = "X-Current-Role", required = false) String currentRole,
            @RequestBody User user) {
        requireAdmin(currentRole);
        validateUser(user);
        return userService.update(id, user)
            .thenApply(updated -> {
                if (updated == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                }
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
            @RequestHeader(value = "X-Current-Role", required = false) String currentRole) {
        requireAdmin(currentRole);
        return userService.delete(id)
            .thenApply(deleted -> {
                if (!deleted) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                }
                return ResponseEntity.<Void>noContent().build();
            });
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置用户密码", description = "仅超级管理员可操作；返回本次设置的新密码用于管理员告知用户")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> resetPassword(
            @Parameter(description = "用户ID", example = "1") @PathVariable Long id,
            @RequestHeader(value = "X-Current-Role", required = false) String currentRole,
            @RequestBody(required = false) Map<String, String> body) {
        requireAdmin(currentRole);
        String newPassword = body == null ? null : body.get("password");
        if (newPassword == null || newPassword.isBlank()) newPassword = "123456";
        final String password = newPassword;
        return userService.resetPassword(id, password)
            .thenApply(user -> {
                if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", user.getId());
                result.put("username", user.getUsername());
                result.put("password", password);
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
        content = @Content(schema = @Schema(example = "{\"username\":\"张三\",\"password\":\"123456\"}"))
    )
    public CompletableFuture<ResponseEntity<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        return userService.login(body.get("username"), body.get("password"))
            .thenApply(user -> {
                if (user == null) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
                }
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
    }

    private void requireAdmin(String role) {
        if (!"admin".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可管理用户账号");
        }
    }

    private void requireManager(String role) {
        if (!"admin".equals(role) && !"technician".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员或审批主管可查看用户账号");
        }
    }
}
