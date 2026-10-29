package com.example.shixun.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

/**
 * Single write path for consumer point income. Every reward (check-in,
 * activities, invite, usage duration) must go through this service so the
 * balance and its ledger transaction stay consistent and the lazily created
 * account always carries the documented first-time gift.
 */
@Service
public class ConsumerPointsRewardService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    @Value("${consumer.credit.initial-balance:240}")
    private BigDecimal initialBalance;

    public ConsumerPointsRewardService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void ensureAccount(long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM consumer_credit_account WHERE user_id=?", Integer.class, userId);
        if (count != null && count > 0) return;
        BigDecimal initial = initialBalance == null ? BigDecimal.ZERO : initialBalance.max(BigDecimal.ZERO);
        try {
            jdbc.update("INSERT INTO consumer_credit_account (user_id,balance,total_recharged) VALUES (?,?,?)", userId, initial, initial);
        } catch (DuplicateKeyException concurrentInitialization) {
            return;
        }
        if (initial.compareTo(BigDecimal.ZERO) > 0) {
            jdbc.update("INSERT INTO consumer_credit_transaction (transaction_no,user_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    "ACT-" + UUID.randomUUID(), userId, "initial", initial, "recharge", "completed", BigDecimal.ZERO, initial, "系统初始化赠送额度", "system");
        }
    }

    /**
     * Credits points and writes the matching ledger row. Participates in an
     * existing transaction when the caller already owns one.
     */
    public void grant(long userId, int points, String remark) {
        if (points <= 0) return;
        transactions.execute(status -> {
            ensureAccount(userId);
            Map<String, Object> row = jdbc.queryForMap("SELECT balance FROM consumer_credit_account WHERE user_id=? FOR UPDATE", userId);
            BigDecimal before = new BigDecimal(String.valueOf(row.get("balance")));
            BigDecimal amount = BigDecimal.valueOf(points);
            BigDecimal after = before.add(amount);
            jdbc.update("UPDATE consumer_credit_account SET balance=? WHERE user_id=?", after, userId);
            KeyHolder key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO consumer_credit_transaction (transaction_no,user_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, "ACT-" + UUID.randomUUID());
                ps.setLong(2, userId);
                ps.setString(3, "reward");
                ps.setBigDecimal(4, amount);
                ps.setString(5, "income");
                ps.setString(6, "completed");
                ps.setBigDecimal(7, before);
                ps.setBigDecimal(8, after);
                ps.setString(9, remark);
                ps.setString(10, "system");
                return ps;
            }, key);
            return null;
        });
    }
}
