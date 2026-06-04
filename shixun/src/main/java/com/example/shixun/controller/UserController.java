package com.example.shixun.controller;

import com.example.shixun.model.User;
import com.example.shixun.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户的增删改查及登录接口")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "获取所有用户", description = "返回全部用户列表")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public CompletableFuture<List<User>> findAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取用户", description = "根据用户ID查询单个用户")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public CompletableFuture<ResponseEntity<User>> findById(
            @Parameter(description = "用户ID", example = "1") @PathVariable Long id) {
        return userService.findById(id)
            .thenApply(user -> {
                if (user == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                }
                return ResponseEntity.ok(user);
            });
    }

    @PostMapping
    @Operation(summary = "新增用户", description = "创建新用户，用户名、年龄、邮箱、密码为必填项")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "409", description = "用户名已存在")
    })
    public CompletableFuture<ResponseEntity<User>> create(@RequestBody User user) {
        validateUser(user);
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        return userService.save(user)
            .thenApply(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
            .exceptionally(ex -> {
                Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                if (cause instanceof IllegalArgumentException) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, cause.getMessage());
                }
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new RuntimeException(cause);
            });
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "根据ID更新用户信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public CompletableFuture<ResponseEntity<User>> update(
            @Parameter(description = "用户ID", example = "1") @PathVariable Long id,
            @RequestBody User user) {
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
            @Parameter(description = "用户ID", example = "1") @PathVariable Long id) {
        return userService.delete(id)
            .thenApply(deleted -> {
                if (!deleted) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                }
                return ResponseEntity.<Void>noContent().build();
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
    public CompletableFuture<ResponseEntity<User>> login(@RequestBody Map<String, String> body) {
        return userService.login(body.get("username"), body.get("password"))
            .thenApply(user -> {
                if (user == null) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
                }
                return ResponseEntity.ok(user);
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
    }
}
