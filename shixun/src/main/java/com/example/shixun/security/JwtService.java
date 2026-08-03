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
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {
    private static final String ISSUER = "smart-pig";
    private static final String AUDIENCE = "smart-pig-api";
    private static final int MIN_SECRET_LENGTH = 32;
    private static final long MEDIA_TOKEN_TTL_SECONDS = 300;
    private static final Set<String> SUPPORTED_ROLES = Set.of("admin", "technician", "feeder", "designer", "user");

    private final ObjectMapper mapper;
    private final byte[] secret;
    private final long expiresSeconds;

    public JwtService(ObjectMapper mapper,
                      @Value("${auth.jwt.secret}") String secret,
                      @Value("${auth.jwt.expires-seconds:28800}") long expiresSeconds) {
        this.mapper = mapper;
        if (secret == null || secret.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("auth.jwt.secret 必须由部署环境提供，且至少需要32个字符");
        }
        if (secret.contains("change-this") || secret.contains("development-jwt-secret")) {
            throw new IllegalStateException("auth.jwt.secret 不能使用示例或开发默认值");
        }
        if (expiresSeconds < 900 || expiresSeconds > 86400) {
            throw new IllegalStateException("auth.jwt.expires-seconds 必须在900到86400秒之间");
        }
        this.secret = secret.trim().getBytes(StandardCharsets.UTF_8);
        this.expiresSeconds = expiresSeconds;
    }

    public String issue(User user) {
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", ISSUER);
            payload.put("aud", AUDIENCE);
            payload.put("sub", String.valueOf(user.getId()));
            payload.put("username", user.getUsername());
            payload.put("role", user.getRole());
            payload.put("iat", now);
            payload.put("exp", now + expiresSeconds);
            payload.put("jti", UUID.randomUUID().toString());
            return sign(payload);
        } catch (Exception e) { throw new IllegalStateException("JWT签发失败", e); }
    }

    /**
     * Issues a browser-safe, short-lived token for one private asset. It is
     * intentionally not a general login token and cannot be used for APIs.
     */
    public String issueMediaAccessToken(Long userId, String username, String role, Long assetId) {
        if (userId == null || userId <= 0 || assetId == null || assetId <= 0
                || username == null || username.isBlank() || !SUPPORTED_ROLES.contains(role)) {
            throw new IllegalArgumentException("媒体访问令牌参数无效");
        }
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", ISSUER);
            payload.put("aud", AUDIENCE);
            payload.put("sub", String.valueOf(userId));
            payload.put("username", username);
            payload.put("role", role);
            payload.put("scope", "asset:read");
            payload.put("assetId", assetId);
            payload.put("iat", now);
            payload.put("exp", now + MEDIA_TOKEN_TTL_SECONDS);
            payload.put("jti", UUID.randomUUID().toString());
            return sign(payload);
        } catch (Exception e) { throw new IllegalStateException("媒体访问令牌签发失败", e); }
    }

    public Claims verify(String token) {
        VerifiedToken verified = verifyAndRead(token);
        if (verified.payload().containsKey("scope") || verified.payload().containsKey("assetId")) {
            throw new IllegalArgumentException("受限媒体令牌不能用于通用接口");
        }
        return verified.claims();
    }

    public Claims verifyMediaAccessToken(String token, long expectedAssetId) {
        VerifiedToken verified = verifyAndRead(token);
        try {
            if (!"asset:read".equals(String.valueOf(verified.payload().get("scope")))) {
                throw new IllegalArgumentException("媒体访问令牌权限无效");
            }
            long assetId = Long.parseLong(String.valueOf(verified.payload().get("assetId")));
            if (assetId != expectedAssetId || verified.claims().expiresAt() - verified.issuedAt() > MEDIA_TOKEN_TTL_SECONDS) {
                throw new IllegalArgumentException("媒体访问令牌无效");
            }
            return verified.claims();
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("媒体访问令牌无效"); }
    }

    private VerifiedToken verifyAndRead(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3) throw new IllegalArgumentException("令牌格式错误");
            byte[] supplied = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(supplied, hmac(parts[0] + "." + parts[1]))) throw new IllegalArgumentException("令牌签名无效");
            Map<String, Object> header = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), new TypeReference<Map<String, Object>>() {});
            if (!"HS256".equals(String.valueOf(header.get("alg"))) || !"JWT".equals(String.valueOf(header.get("typ")))) {
                throw new IllegalArgumentException("令牌算法无效");
            }
            Map<String, Object> payload = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<Map<String, Object>>() {});
            if (!ISSUER.equals(String.valueOf(payload.get("iss")))) throw new IllegalArgumentException("令牌签发方无效");
            if (!AUDIENCE.equals(String.valueOf(payload.get("aud")))) throw new IllegalArgumentException("令牌受众无效");
            long exp = Long.parseLong(String.valueOf(payload.get("exp")));
            if (Instant.now().getEpochSecond() >= exp) throw new IllegalArgumentException("登录已过期");
            Long userId = Long.valueOf(String.valueOf(payload.get("sub")));
            String username = String.valueOf(payload.get("username"));
            String role = String.valueOf(payload.get("role"));
            long issuedAt = Long.parseLong(String.valueOf(payload.get("iat")));
            long now = Instant.now().getEpochSecond();
            if (userId <= 0 || username.isBlank() || !SUPPORTED_ROLES.contains(role) || issuedAt > now + 60 || exp <= issuedAt) {
                throw new IllegalArgumentException("令牌内容无效");
            }
            return new VerifiedToken(new Claims(userId, username, role, exp), issuedAt, payload);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("令牌无效"); }
    }

    public long expiresSeconds() { return expiresSeconds; }
    private String sign(Map<String, Object> payload) throws Exception {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        String signingInput = encode(mapper.writeValueAsBytes(header)) + "." + encode(mapper.writeValueAsBytes(payload));
        return signingInput + "." + encode(hmac(signingInput));
    }
    private byte[] hmac(String input) throws Exception { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return mac.doFinal(input.getBytes(StandardCharsets.UTF_8)); }
    private String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private record VerifiedToken(Claims claims, long issuedAt, Map<String, Object> payload) {}
    public record Claims(Long userId, String username, String role, long expiresAt) {}
}
