package com.example.shixun.security;

import com.example.shixun.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private final ObjectMapper mapper;
    private final byte[] secret;
    private final long expiresSeconds;

    public JwtService(ObjectMapper mapper,
                      @Value("${auth.jwt.secret:change-this-development-jwt-secret-before-production-2026-and-keep-it-private}") String secret,
                      @Value("${auth.jwt.expires-seconds:28800}") long expiresSeconds) {
        this.mapper = mapper;
        if (secret == null || secret.trim().length() < 32) throw new IllegalStateException("auth.jwt.secret 至少需要32个字符");
        this.secret = secret.trim().getBytes(StandardCharsets.UTF_8);
        this.expiresSeconds = Math.max(900, expiresSeconds);
    }

    public String issue(User user) {
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", "smart-pig");
            payload.put("sub", String.valueOf(user.getId()));
            payload.put("username", user.getUsername());
            payload.put("role", user.getRole());
            payload.put("iat", now);
            payload.put("exp", now + expiresSeconds);
            String signingInput = encode(mapper.writeValueAsBytes(header)) + "." + encode(mapper.writeValueAsBytes(payload));
            return signingInput + "." + encode(hmac(signingInput));
        } catch (Exception e) { throw new IllegalStateException("JWT签发失败", e); }
    }

    public Claims verify(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("令牌格式错误");
            byte[] supplied = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(supplied, hmac(parts[0] + "." + parts[1]))) throw new IllegalArgumentException("令牌签名无效");
            Map<String, Object> payload = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<Map<String, Object>>() {});
            if (!"smart-pig".equals(String.valueOf(payload.get("iss")))) throw new IllegalArgumentException("令牌签发方无效");
            long exp = Long.parseLong(String.valueOf(payload.get("exp")));
            if (Instant.now().getEpochSecond() >= exp) throw new IllegalArgumentException("登录已过期");
            Long userId = Long.valueOf(String.valueOf(payload.get("sub")));
            String username = String.valueOf(payload.get("username"));
            String role = String.valueOf(payload.get("role"));
            if (username.isBlank() || role.isBlank()) throw new IllegalArgumentException("令牌内容无效");
            return new Claims(userId, username, role, exp);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("令牌无效"); }
    }

    public long expiresSeconds() { return expiresSeconds; }
    private byte[] hmac(String input) throws Exception { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return mac.doFinal(input.getBytes(StandardCharsets.UTF_8)); }
    private String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public record Claims(Long userId, String username, String role, long expiresAt) {}
}
