package com.example.shixun.service;

import com.example.shixun.mapper.UserMapper;
import com.example.shixun.model.User;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Async
    public CompletableFuture<List<User>> findAll() {
        return CompletableFuture.completedFuture(userMapper.findAll());
    }

    @Async
    public CompletableFuture<User> findById(Long id) {
        return CompletableFuture.completedFuture(userMapper.findById(id));
    }

    @Async
    public CompletableFuture<User> save(User user) {
        if (userMapper.findByUsername(user.getUsername()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        user.setPassword(null);
        return CompletableFuture.completedFuture(user);
    }

    @Async
    public CompletableFuture<User> update(Long id, User user) {
        User existing = userMapper.findById(id);
        if (existing == null) return CompletableFuture.completedFuture(null);
        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(existing.getPassword());
        }
        userMapper.update(user);
        user.setPassword(null);
        return CompletableFuture.completedFuture(user);
    }

    @Async
    public CompletableFuture<Boolean> delete(Long id) {
        return CompletableFuture.completedFuture(userMapper.deleteById(id) > 0);
    }

    @Async
    public CompletableFuture<User> login(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        User user = userMapper.findByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return CompletableFuture.completedFuture(null);
        }
        user.setPassword(null);
        return CompletableFuture.completedFuture(user);
    }
}
