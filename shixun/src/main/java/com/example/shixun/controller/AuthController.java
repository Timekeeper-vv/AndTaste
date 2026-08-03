package com.example.shixun.controller;

import com.example.shixun.model.User;
import com.example.shixun.service.UserService;
import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;
    public AuthController(UserService userService, JwtService jwtService) { this.userService = userService; this.jwtService = jwtService; }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE) JwtService.Claims principal) {
        // The JWT filter has already verified the signature and matched the
        // canonical user row.  Read the id from that server-side attribute;
        // never trust a caller-supplied X-Current-User-Id header.
        User user = userService.findById(principal.userId()).join();
        if (user == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在或已被删除");
        user.setPassword(null);
        return Map.of("user", user);
    }
}
