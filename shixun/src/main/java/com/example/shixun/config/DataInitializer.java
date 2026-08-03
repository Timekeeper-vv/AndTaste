package com.example.shixun.config;

import com.example.shixun.mapper.UserMapper;
import com.example.shixun.model.User;
import com.example.shixun.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Production intentionally has no built-in usernames or passwords. A first
 * administrator can only be provisioned once by explicitly enabling this
 * bootstrap and supplying all values through the deployment environment.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    @Bean
    @ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
    ApplicationRunner bootstrapInitialAdmin(
            UserService userService,
            UserMapper userMapper,
            @Value("${app.bootstrap.admin.username:}") String username,
            @Value("${app.bootstrap.admin.password:}") String password,
            @Value("${app.bootstrap.admin.email:}") String email,
            @Value("${app.bootstrap.admin.phone:}") String phone,
            @Value("${app.bootstrap.admin.age:30}") Integer age) {
        return args -> {
            validateBootstrap(username, password, email, phone, age);
            if (userMapper.findByUsername(username.trim()) != null) {
                log.info("Initial administrator already exists; no password or role was changed");
                return;
            }

            User admin = new User();
            admin.setUsername(username.trim());
            admin.setPassword(password);
            admin.setEmail(email.trim());
            admin.setPhone(phone.trim());
            admin.setAge(age);
            admin.setRole("admin");
            userService.save(admin).join();
            log.info("Initial administrator was provisioned from explicit deployment configuration");
        };
    }

    private void validateBootstrap(String username, String password, String email, String phone, Integer age) {
        if (blank(username) || blank(password) || blank(email) || blank(phone)) {
            throw new IllegalStateException("启用 app.bootstrap.admin 时，必须同时配置用户名、密码、邮箱和手机号");
        }
        if (!password.equals(password.trim()) || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("初始管理员密码至少需要" + MIN_PASSWORD_LENGTH + "个字符，且首尾不能包含空格");
        }
        if (age == null || age <= 0) {
            throw new IllegalStateException("初始管理员年龄必须大于0");
        }
        if (!phone.trim().matches("^[0-9+()\\-\\s]{6,30}$")) {
            throw new IllegalStateException("初始管理员手机号格式不正确");
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
