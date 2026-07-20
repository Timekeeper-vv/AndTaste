package com.example.shixun.config;

import com.example.shixun.mapper.UserMapper;
import com.example.shixun.model.User;
import com.example.shixun.service.UserService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    ApplicationRunner initData(UserService userService, UserMapper userMapper) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return args -> {
            if (userService.findAll().join().isEmpty()) {
                User u1 = new User(); u1.setUsername("superadmin"); u1.setAge(30); u1.setEmail("superadmin@andtaste.com"); u1.setPhone("13800000001"); u1.setPassword("123456"); u1.setRole("admin");
                User u2 = new User(); u2.setUsername("approver01"); u2.setAge(28); u2.setEmail("approver01@andtaste.com"); u2.setPhone("13800000002"); u2.setPassword("123456"); u2.setRole("technician");
                User u3 = new User(); u3.setUsername("employee01"); u3.setAge(24); u3.setEmail("employee01@andtaste.com"); u3.setPhone("13800000003"); u3.setPassword("123456"); u3.setRole("feeder");
                userService.save(u1); userService.save(u2); userService.save(u3);
            }

            ensureApprover(userService, userMapper, "审批员1", "approver1@andtaste.com", "13800000101");
            ensureApprover(userService, userMapper, "审批员2", "approver2@andtaste.com", "13800000102");
            ensureApprover(userService, userMapper, "审批员3", "approver3@andtaste.com", "13800000103");
            ensureApprover(userService, userMapper, "审批员4", "approver4@andtaste.com", "13800000104");

            // 修复 schema.sql 直接插入的明文密码：BCrypt 哈希以 $2 开头，不是则说明是明文
            userService.findAll().join().forEach(u -> {
                User full = userMapper.findById(u.getId());
                String pwd = full.getPassword();
                if (pwd != null && !pwd.startsWith("$2")) {
                    full.setPassword(encoder.encode(pwd));
                    userMapper.update(full);
                }
            });
        };
    }

    private void ensureApprover(UserService userService, UserMapper userMapper, String username, String email, String phone) {
        if (userMapper.findByUsername(username) != null) return;
        User u = new User();
        u.setUsername(username);
        u.setAge(28);
        u.setEmail(email);
        u.setPhone(phone);
        u.setPassword("123456");
        u.setRole("technician");
        userService.save(u).join();
    }
}
