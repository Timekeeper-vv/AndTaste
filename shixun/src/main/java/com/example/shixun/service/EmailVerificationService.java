package com.example.shixun.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class EmailVerificationService {
    private static final String PURPOSE = "registration";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final boolean enabled;
    private final String from;
    private final String secret;

    public EmailVerificationService(
            JdbcTemplate jdbc,
            ObjectProvider<JavaMailSender> mailSenders,
            @Value("${app.email.verification-enabled:false}") boolean enabled,
            @Value("${app.email.from:}") String from,
            @Value("${app.email.verification-secret:}") String secret) {
        this.jdbc = jdbc;
        this.mailSenders = mailSenders;
        this.enabled = enabled;
        this.from = from == null ? "" : from.trim();
        this.secret = secret == null ? "" : secret.trim();
    }

    public void sendRegistrationCode(String email, String requestIp) {
        String normalizedEmail = normalizeEmail(email);
        ensureConfigured();
        Number recent = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_email_verification WHERE email=? AND purpose=? AND created_at > ?",
                Number.class, normalizedEmail, PURPOSE, LocalDateTime.now().minusSeconds(60));
        if (recent != null && recent.intValue() > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码已发送，请60秒后再试");
        }

        String ipHash = digestIp(requestIp);
        if (ipHash != null) {
            Number recentFromIp = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_email_verification WHERE requested_ip_hash=? AND purpose=? AND created_at > ?",
                    Number.class, ipHash, PURPOSE, LocalDateTime.now().minusMinutes(10));
            if (recentFromIp != null && recentFromIp.intValue() >= 5) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String hash = digest(normalizedEmail, code);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        jdbc.update("INSERT INTO user_email_verification(email,purpose,code_hash,expires_at,requested_ip_hash) VALUES (?,?,?,?,?)",
                normalizedEmail, PURPOSE, hash, expiresAt, ipHash);
        try {
            JavaMailSender sender = mailSenders.getIfAvailable();
            if (sender == null) throw new IllegalStateException("SMTP 未配置");
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(normalizedEmail);
            message.setSubject("之间智造邮箱验证码");
            message.setText("你的注册验证码是：" + code + "\n\n验证码5分钟内有效，仅可使用一次。请勿将验证码告诉他人。\n如果不是你本人操作，请忽略此邮件。");
            sender.send(message);
        } catch (RuntimeException error) {
            jdbc.update("DELETE FROM user_email_verification WHERE email=? AND purpose=? AND code_hash=?",
                    normalizedEmail, PURPOSE, hash);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码邮件发送失败，请稍后重试");
        }
    }

    public void consumeRegistrationCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱验证码格式不正确");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,code_hash,expires_at,attempts FROM user_email_verification "
                        + "WHERE email=? AND purpose=? AND used_at IS NULL ORDER BY id DESC LIMIT 1",
                normalizedEmail, PURPOSE);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先获取邮箱验证码");
        Map<String, Object> row = rows.get(0);
        long id = ((Number) row.get("id")).longValue();
        int attempts = ((Number) row.get("attempts")).intValue();
        if (attempts >= MAX_ATTEMPTS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码错误次数过多，请重新获取");
        jdbc.update("UPDATE user_email_verification SET attempts=attempts+1 WHERE id=? AND used_at IS NULL", id);

        Object expires = row.get("expires_at");
        if (expires instanceof java.sql.Timestamp timestamp && timestamp.toLocalDateTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        String expected = String.valueOf(row.get("code_hash"));
        String actual = digest(normalizedEmail, code.trim());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱验证码错误");
        }
        int updated = jdbc.update("UPDATE user_email_verification SET used_at=CURRENT_TIMESTAMP WHERE id=? AND used_at IS NULL", id);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已使用，请重新获取");
    }

    public String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        }
        return value;
    }

    private void ensureConfigured() {
        if (!enabled || from.isBlank() || secret.length() < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮箱验证码服务尚未配置");
        }
    }

    private String digest(String email, String code) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((secret + ":" + email + ":" + code).getBytes(StandardCharsets.UTF_8));
            return hex(bytes);
        } catch (Exception error) {
            throw new IllegalStateException("服务器缺少 SHA-256 实现", error);
        }
    }

    private String digestIp(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(ip.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("服务器缺少 SHA-256 实现", error);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }
}
