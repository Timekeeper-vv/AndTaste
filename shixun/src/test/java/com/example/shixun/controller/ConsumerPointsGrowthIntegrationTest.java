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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Test
    void bountySubmissionRejectsInvalidIdCardAndPhone() throws Exception {
        TestUser user = createUser("bounty-identity");
        TestUser other = createUser("bounty-other");
        jdbc.update("INSERT INTO consumer_bounty_task (campaign_key,title,brief,reward_amount,status) VALUES ('bounty-id','身份校验测试','说明',2000,'active')");
        Long taskId = jdbc.queryForObject("SELECT id FROM consumer_bounty_task WHERE campaign_key='bounty-id'", Long.class);
        jdbc.update("INSERT INTO creative_multiview_bundle (bundle_no,user_id,product_name,view_count,status) VALUES ('B-ID',?,'他人作品包',3,'ready')", other.id());
        Long bundleId = jdbc.queryForObject("SELECT id FROM creative_multiview_bundle WHERE bundle_no='B-ID'", Long.class);
        String base = "{\"bundleId\":" + bundleId + ",\"viewAssetIds\":[1,2,3],"
                + "\"productName\":\"产品\",\"productCategory\":\"品类\",\"productFeatures\":\"特色\",\"artifactPrototype\":\"原型\",\"productDescription\":\"介绍\",";

        // 身份证号只有 17 位：直接拦截并提示
        expectSubmissionRejected(taskId, base + "\"idCard\":\"11010119900101123\",\"contactPhone\":\"13800138000\"}", user, "身份证号必须为18位");
        // 身份证号 19 位：超出长度上限，同样拦截
        expectSubmissionRejected(taskId, base + "\"idCard\":\"1101011990010112345\",\"contactPhone\":\"13800138000\"}", user, "idCard内容过长");
        // 身份证号中间含字母（仅末位允许 X）：拦截
        expectSubmissionRejected(taskId, base + "\"idCard\":\"11010119900101X234\",\"contactPhone\":\"13800138000\"}", user, "身份证号必须为18位");
        // 手机号只有 10 位：拦截
        expectSubmissionRejected(taskId, base + "\"idCard\":\"11010119900101123X\",\"contactPhone\":\"1380013800\"}", user, "手机号必须为11位");
        // 手机号 12 位：拦截
        expectSubmissionRejected(taskId, base + "\"idCard\":\"11010119900101123X\",\"contactPhone\":\"138001380000\"}", user, "手机号必须为11位");

        // 身份证末位 X、手机号 11 位：通过位数校验，继续进入后续业务校验（此处因作品包非本人而被拦截）
        mvc.perform(post(BASE + "/tasks/" + taskId + "/submissions")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType("application/json")
                        .content(base + "\"idCard\":\"11010119900101123X\",\"contactPhone\":\"13800138000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请选择本人完整的三视图作品包"));

        // 所有被拒绝的请求都不应落库
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_bounty_submission", Integer.class)).isZero();
    }

    private void expectSubmissionRejected(long taskId, String body, TestUser user, String message) throws Exception {
        mvc.perform(post(BASE + "/tasks/" + taskId + "/submissions")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(message)));
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
