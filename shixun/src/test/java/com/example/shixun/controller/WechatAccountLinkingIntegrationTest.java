package com.example.shixun.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that a verified website account and wx_* placeholder share one work library. */
@SpringBootTest
@ActiveProfiles("test")
class WechatAccountLinkingIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserController controller;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:wechat_account_linking;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void schema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS wechat_user_binding (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,app_id VARCHAR(64) NOT NULL,openid VARCHAR(128) NOT NULL,UNIQUE(user_id,app_id),UNIQUE(app_id,openid))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_conversation_session (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_conversation_event (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_quote_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_consignment_application (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_multiview_bundle (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS consumer_production_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS commercial_application_revision (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS commercial_professional_guidance_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_selection_recommendation (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS selection_demand_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL)");
        jdbc.update("DELETE FROM wechat_user_binding");
        jdbc.update("DELETE FROM digital_asset");
        jdbc.update("DELETE FROM ai_generation_job");
        jdbc.update("DELETE FROM creative_conversation_session");
        jdbc.update("DELETE FROM creative_conversation_event");
        jdbc.update("DELETE FROM creative_quote_request");
        jdbc.update("DELETE FROM creative_consignment_application");
        jdbc.update("DELETE FROM creative_multiview_bundle");
        jdbc.update("DELETE FROM consumer_production_request");
        jdbc.update("DELETE FROM commercial_application_revision");
        jdbc.update("DELETE FROM commercial_professional_guidance_request");
        jdbc.update("DELETE FROM creative_selection_recommendation");
        jdbc.update("DELETE FROM selection_demand_request");
        jdbc.update("DELETE FROM user");
    }

    @Test
    void mergesAnAutoCreatedWechatAccountAndMovesItsWorks() throws Exception {
        long webUserId = user("web-owner");
        long wxUserId = user("wx_placeholder");
        jdbc.update("INSERT INTO wechat_user_binding(user_id,app_id,openid) VALUES (?,?,?)", wxUserId, "mini-app", "openid-1");
        jdbc.update("INSERT INTO digital_asset(asset_no,title,asset_type,status,created_by) VALUES (?,?,?,?,?)",
                "AST-LINK-1", "之间智造效果图", "image", "draft", wxUserId);
        jdbc.update("INSERT INTO ai_generation_job(job_no,job_type,provider,status,created_by) VALUES (?,?,?,?,?)",
                "JOB-LINK-1", "text_to_image", "test", "succeeded", wxUserId);
        jdbc.update("INSERT INTO creative_multiview_bundle(bundle_no,user_id) VALUES (?,?)", "MVB-LINK-1", wxUserId);

        Map<String, Object> result = invokeLink(webUserId, "mini-app", "openid-1");

        assertThat(result).containsEntry("bound", true).containsEntry("merged", true).containsEntry("movedAssets", 1);
        assertThat(jdbc.queryForObject("SELECT user_id FROM wechat_user_binding WHERE app_id=? AND openid=?", Long.class, "mini-app", "openid-1"))
                .isEqualTo(webUserId);
        assertThat(jdbc.queryForObject("SELECT created_by FROM digital_asset WHERE asset_no='AST-LINK-1'", Long.class)).isEqualTo(webUserId);
        assertThat(jdbc.queryForObject("SELECT created_by FROM ai_generation_job WHERE job_no='JOB-LINK-1'", Long.class)).isEqualTo(webUserId);
        assertThat(jdbc.queryForObject("SELECT user_id FROM creative_multiview_bundle", Long.class)).isEqualTo(webUserId);
    }

    private long user(String username) {
        jdbc.update("INSERT INTO user(username,age,email,password,role,status) VALUES (?,?,?,?,?,?)",
                username, 30, username + "@example.com", "not-used-in-this-test", "user", "active");
        return jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeLink(long targetUserId, String appId, String openId) throws Exception {
        Class<?> identityType = Class.forName("com.example.shixun.controller.UserController$WechatIdentity");
        Constructor<?> constructor = identityType.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        Object identity = constructor.newInstance(appId, openId);
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                controller, "linkAuthenticatedWechatIdentity", targetUserId, identity);
    }
}
