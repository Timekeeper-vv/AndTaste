package com.example.shixun.controller;

import com.example.shixun.model.User;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.ConsumerPointsInviteService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 积分成长玩法（使用时长 / 分享朋友圈 / 邀请拉新）的数值与幂等校验。
 * 数值来源：20261029-小程序补充.xlsx「积分玩法」工作表。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsumerPointsGrowthIntegrationTest {
    private static final String BASE = "/api/creative/ai/consumer-activities";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;
    @Autowired ConsumerPointsInviteService invites;

    @DynamicPropertySource
    static void growthProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:points_growth_test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM consumer_points_usage_daily");
        jdbc.update("DELETE FROM consumer_points_share_claim");
        jdbc.update("DELETE FROM consumer_points_invite_record");
        jdbc.update("DELETE FROM consumer_points_invite_code");
        jdbc.update("DELETE FROM consumer_points_checkin");
        jdbc.update("DELETE FROM consumer_points_red_packet_claim");
        jdbc.update("DELETE FROM consumer_points_red_packet_event");
        jdbc.update("DELETE FROM consumer_points_quiz_attempt");
        jdbc.update("DELETE FROM consumer_points_quiz_session_question");
        jdbc.update("DELETE FROM consumer_points_quiz_session");
        jdbc.update("DELETE FROM consumer_points_quiz_question");
        jdbc.update("DELETE FROM consumer_bounty_sample_voucher");
        jdbc.update("DELETE FROM consumer_bounty_submission");
        jdbc.update("DELETE FROM consumer_bounty_task");
        jdbc.update("DELETE FROM consumer_credit_transaction");
        jdbc.update("DELETE FROM consumer_credit_account");
        jdbc.update("DELETE FROM user");
    }

    @Test
    void usageHeartbeatPaysFivePointsPerMinuteAndCapsAtOneThousand() throws Exception {
        TestUser user = createUser("growth-usage");
        JsonNode overview = getJson(BASE + "/overview", user.token());
        assertThat(overview.path("usage").path("pointsPerMinute").asInt()).isEqualTo(5);
        assertThat(overview.path("usage").path("dailyPointCap").asInt()).isEqualTo(1000);

        JsonNode first = postJson(BASE + "/usage/heartbeat", "{\"activeSeconds\":60}", user.token());
        assertThat(first.path("points").asInt()).isEqualTo(5);
        assertThat(first.path("granted").asInt()).isEqualTo(5);
        assertThat(balance(user.id())).isEqualByComparingTo("245");

        JsonNode second = postJson(BASE + "/usage/heartbeat", "{\"activeSeconds\":60}", user.token());
        assertThat(second.path("points").asInt()).isEqualTo(10);
        assertThat(second.path("granted").asInt()).isEqualTo(5);

        // 单次最多按 60 秒结算，多报的秒数会被截断，无法一次刷满全天额度。
        JsonNode clamped = postJson(BASE + "/usage/heartbeat", "{\"activeSeconds\":9999}", user.token());
        assertThat(clamped.path("activeSeconds").asInt()).isEqualTo(180);

        // 直接把当日累计推到距上限一分钟，验证每日 1000 分封顶（避免触发接口限流）。
        jdbc.update("UPDATE consumer_points_usage_daily SET active_seconds=11940,awarded_points=995 WHERE user_id=?", user.id());
        JsonNode capped = postJson(BASE + "/usage/heartbeat", "{\"activeSeconds\":60}", user.token());
        assertThat(capped.path("activeSeconds").asInt()).isEqualTo(12000);
        assertThat(capped.path("points").asInt()).isEqualTo(1000);
        assertThat(capped.path("granted").asInt()).isEqualTo(5);
        assertThat(balance(user.id())).isEqualByComparingTo("260");

        JsonNode afterCap = postJson(BASE + "/usage/heartbeat", "{\"activeSeconds\":60}", user.token());
        assertThat(afterCap.path("points").asInt()).isEqualTo(1000);
        assertThat(afterCap.path("granted").asInt()).isZero();
        assertThat(balance(user.id())).isEqualByComparingTo("260");
    }

    @Test
    void shareRewardIsGrantedOnlyOncePerDay() throws Exception {
        TestUser user = createUser("growth-share");
        JsonNode claimed = postJson(BASE + "/share/claim", "{\"channel\":\"timeline\"}", user.token());
        assertThat(claimed.path("points").asInt()).isEqualTo(80);
        assertThat(balance(user.id())).isEqualByComparingTo("320");

        mvc.perform(post(BASE + "/share/claim")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType("application/json")
                        .content("{\"channel\":\"timeline\"}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_points_share_claim WHERE user_id=?", Integer.class, user.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_credit_transaction WHERE user_id=? AND biz_type='reward' AND amount=80", Integer.class, user.id()))
                .isEqualTo(1);
    }

    @Test
    void inviteRewardsInviterAndInviteeExactlyOnce() {
        TestUser inviter = createUser("growth-inviter");
        TestUser invitee = createUser("growth-invitee");
        String code = invites.ensureCode(inviter.id());
        assertThat(code).hasSize(8);

        assertThat(invites.bindOnRegistration(invitee.id(), code)).isTrue();
        assertThat(balance(inviter.id())).isEqualByComparingTo("320");
        assertThat(balance(invitee.id())).isEqualByComparingTo("440");
        assertThat(jdbc.queryForObject(
                "SELECT invite_code FROM consumer_points_invite_record WHERE invitee_id=?", String.class, invitee.id()))
                .isEqualTo(code);
        assertThat(jdbc.queryForObject(
                "SELECT inviter_points FROM consumer_points_invite_record WHERE invitee_id=?", Integer.class, invitee.id()))
                .isEqualTo(80);
        assertThat(jdbc.queryForObject(
                "SELECT invitee_points FROM consumer_points_invite_record WHERE invitee_id=?", Integer.class, invitee.id()))
                .isEqualTo(200);

        // 同一账号不能重复绑定，也不会重复发放。
        assertThat(invites.bindOnRegistration(invitee.id(), code)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_points_invite_record WHERE invitee_id=?", Integer.class, invitee.id())).isEqualTo(1);
        assertThat(balance(invitee.id())).isEqualByComparingTo("440");

        // 无效邀请码、自己邀请自己都不生效。
        assertThat(invites.bindOnRegistration(createUser("growth-stranger").id(), "ZZZZZZZZ")).isFalse();
        assertThat(invites.bindOnRegistration(inviter.id(), code)).isFalse();

        var stats = invites.stats(inviter.id());
        assertThat(((Number) stats.get("invitedCount")).intValue()).isEqualTo(1);
        assertThat(((Number) stats.get("pointsEarned")).intValue()).isEqualTo(80);
        assertThat(stats.get("inviterPoints")).isEqualTo(80);
        assertThat(stats.get("inviteePoints")).isEqualTo(200);
    }

    @Test
    void usageHeartbeatRejectsMissingSeconds() throws Exception {
        TestUser user = createUser("growth-invalid");
        mvc.perform(post(BASE + "/usage/heartbeat")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode getJson(String path, String token) throws Exception {
        String body = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body);
    }

    private JsonNode postJson(String path, String body, String token) throws Exception {
        String response = mvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(response);
    }

    private BigDecimal balance(long userId) {
        return jdbc.queryForObject("SELECT balance FROM consumer_credit_account WHERE user_id=?", BigDecimal.class, userId);
    }

    private TestUser createUser(String username) {
        jdbc.update("INSERT INTO user (username,password,role,status) VALUES (?,?,?,?)", username, "test-password", "user", "active");
        Long id = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
        User user = new User(id, username, 20, username + "@test.local", null);
        user.setRole("user");
        return new TestUser(id, jwtService.issue(user));
    }

    private record TestUser(long id, String token) { }
}
