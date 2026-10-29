package com.example.shixun.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Invite-a-friend rewards. The invitee binds at registration time (one
 * binding per account, enforced by a unique key), the inviter receives 80
 * points and the invitee 200 points, matching the published points rules.
 *
 * <p>Binding runs in its own transaction: an invalid, expired, or even
 * colliding code must never fail the surrounding registration.</p>
 */
@Service
public class ConsumerPointsInviteService {
    private static final Logger log = LoggerFactory.getLogger(ConsumerPointsInviteService.class);
    public static final int INVITER_POINTS = 80;
    public static final int INVITEE_POINTS = 200;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ConsumerPointsRewardService rewards;
    private final TransactionTemplate inviteTransactions;

    public ConsumerPointsInviteService(JdbcTemplate jdbc, ConsumerPointsRewardService rewards,
                                       PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.rewards = rewards;
        this.inviteTransactions = new TransactionTemplate(transactionManager);
        this.inviteTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String ensureCode(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT invite_code inviteCode FROM consumer_points_invite_code WHERE user_id=?", userId);
        if (!rows.isEmpty()) return String.valueOf(rows.get(0).get("inviteCode"));
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode();
            try {
                jdbc.update("INSERT INTO consumer_points_invite_code (user_id,invite_code) VALUES (?,?)", userId, code);
                return code;
            } catch (DuplicateKeyException duplicate) {
                List<Map<String, Object>> concurrent = jdbc.queryForList(
                        "SELECT invite_code inviteCode FROM consumer_points_invite_code WHERE user_id=?", userId);
                if (!concurrent.isEmpty()) return String.valueOf(concurrent.get(0).get("inviteCode"));
            }
        }
        throw new IllegalStateException("邀请码生成失败，请稍后重试");
    }

    public Map<String, Object> stats(long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", ensureCode(userId));
        Integer invitedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_points_invite_record WHERE inviter_id=?", Integer.class, userId);
        out.put("invitedCount", invitedCount == null ? 0 : invitedCount);
        Integer pointsEarned = jdbc.queryForObject(
                "SELECT COALESCE(SUM(inviter_points),0) FROM consumer_points_invite_record WHERE inviter_id=?", Integer.class, userId);
        out.put("pointsEarned", pointsEarned == null ? 0 : pointsEarned);
        List<Timestamp> lastRows = jdbc.queryForList(
                "SELECT MAX(created_at) FROM consumer_points_invite_record WHERE inviter_id=?", Timestamp.class, userId);
        out.put("lastInvitedAt", lastRows.isEmpty() ? null : lastRows.get(0));
        out.put("inviterPoints", INVITER_POINTS);
        out.put("inviteePoints", INVITEE_POINTS);
        return out;
    }

    /**
     * Binds a new account to the invite code owner and pays both sides.
     * Returns true only when this call created the binding and the rewards.
     */
    public boolean bindOnRegistration(long inviteeUserId, String rawCode) {
        String code = normalize(rawCode);
        if (code == null) return false;
        try {
            return Boolean.TRUE.equals(inviteTransactions.execute(status -> {
                List<Map<String, Object>> owners = jdbc.queryForList(
                        "SELECT user_id userId FROM consumer_points_invite_code WHERE invite_code=? LIMIT 1", code);
                if (owners.isEmpty()) return false;
                long inviterId = ((Number) owners.get(0).get("userId")).longValue();
                if (inviterId == inviteeUserId) return false;
                Integer inviterActive = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM user WHERE id=? AND role='user'", Integer.class, inviterId);
                if (inviterActive == null || inviterActive == 0) return false;
                Integer existing = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM consumer_points_invite_record WHERE invitee_id=?", Integer.class, inviteeUserId);
                if (existing != null && existing > 0) return false;
                try {
                    jdbc.update("INSERT INTO consumer_points_invite_record (inviter_id,invitee_id,invite_code,inviter_points,invitee_points) VALUES (?,?,?,?,?)",
                            inviterId, inviteeUserId, code, INVITER_POINTS, INVITEE_POINTS);
                } catch (DuplicateKeyException duplicate) {
                    return false;
                }
                rewards.grant(inviterId, INVITER_POINTS, "邀请好友注册奖励");
                rewards.grant(inviteeUserId, INVITEE_POINTS, "接受好友邀请注册奖励");
                return true;
            }));
        } catch (RuntimeException error) {
            log.warn("invite binding skipped for user {}: {}", inviteeUserId, error.getMessage());
            return false;
        }
    }

    private static String normalize(String rawCode) {
        if (rawCode == null) return null;
        String code = rawCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (code.length() != CODE_LENGTH) return null;
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) return null;
        }
        return code;
    }

    private static String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return builder.toString();
    }
}
