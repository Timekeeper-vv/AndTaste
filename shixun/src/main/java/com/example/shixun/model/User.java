package com.example.shixun.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "用户实体")
public class User {

    @Schema(description = "用户ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "用户名", example = "zhangsan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "年龄", example = "25", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer age;

    @Schema(description = "邮箱", example = "zhangsan@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "手机号", example = "13800138001")
    private String phone;

    @Schema(description = "密码（仅写入，响应中不返回；至少 12 个字符）", example = "Str0ngPassw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Schema(description = "角色：admin 超级管理员 / technician 审批主管 / feeder 员工", example = "admin")
    private String role;

    @Schema(description = "账号创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @Schema(description = "最近成功登录时间", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime lastLoginAt;

    @Schema(description = "账号状态", accessMode = Schema.AccessMode.READ_ONLY)
    private String status;

    public User() {
    }

    public User(Long id, String username, Integer age, String email, String phone) {
        this.id = id;
        this.username = username;
        this.age = age;
        this.email = email;
        this.phone = phone;
    }

public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
