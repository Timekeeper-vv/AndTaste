package com.example.shixun.controller;

import com.example.shixun.model.User;
import com.example.shixun.security.JwtService;
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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreatorCampaignRewardIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;

    @DynamicPropertySource
    static void campaignProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:creator_campaign_test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM consumer_campaign_reward");
        jdbc.update("DELETE FROM consumer_credit_transaction");
        jdbc.update("DELETE FROM consumer_credit_account");
        jdbc.update("DELETE FROM digital_asset");
        jdbc.update("DELETE FROM channel_directory");
        jdbc.update("DELETE FROM user");
    }

    @Test
    void loginCanReadOnlyPublicCreatorCampaignBriefsWithoutAuthentication() throws Exception {
        String body = mvc.perform(get("/api/creative/ai/consumer-rewards/campaigns/public"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode campaigns = mapper.readTree(body);
        assertThat(campaigns).hasSize(4);
        assertThat(campaigns.get(0).path("key").asText()).isEqualTo("museum_summer_gift_2026");
        assertThat(campaigns.get(0).path("channelCode").asText()).isEqualTo("museum-national");
        assertThat(campaigns.get(0).path("rewardAmount").asInt()).isEqualTo(80);
        assertThat(campaigns.get(0).has("status")).isFalse();
    }

    @Test
    void selectedCampaignIsRecordedOnSubmissionAndRewardsAfterApproval() throws Exception {
        TestUser creator = createUser("campaign-creator", "user");
        TestUser admin = createUser("campaign-admin", "admin");
        jdbc.update("INSERT INTO channel_directory (channel_code,name,province,city,district,channel_type,source_type,cooperation_status,enabled) VALUES (?,?,?,?,?,?,?,?,1)",
                "museum-national", "中国国家博物馆", "北京市", "北京市", "东城区", "museum", "test", "directory_only");
        long museumId = jdbc.queryForObject("SELECT id FROM channel_directory WHERE channel_code='museum-national'", Long.class);
        String directory = mvc.perform(get("/api/creative/ai/consumer-production/museums")
                        .header("Authorization", "Bearer " + creator.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(directory).contains("museum-national");
        jdbc.update("INSERT INTO digital_asset (asset_no,title,asset_type,source_type,status,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "AST-CAMPAIGN-1", "器物新生冰箱贴", "model", "ai_generated", "draft", creator.id());
        long assetId = jdbc.queryForObject("SELECT id FROM digital_asset WHERE asset_no='AST-CAMPAIGN-1'", Long.class);

        String submitBody = "{\"purpose\":\"museum_sale\",\"museumId\":\"" + museumId
                + "\",\"campaignKey\":\"museum_summer_gift_2026\"}";
        String submission = mvc.perform(put("/api/creative/ai/consumer-assets/{id}/submit-review", assetId)
                        .header("Authorization", "Bearer " + creator.token())
                        .contentType("application/json")
                        .content(submitBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapper.readTree(submission).path("campaignKey").asText()).isEqualTo("museum_summer_gift_2026");
        assertThat(jdbc.queryForObject("SELECT status FROM digital_asset WHERE id=?", String.class, assetId)).isEqualTo("review");
        assertThat(jdbc.queryForObject("SELECT status FROM consumer_campaign_reward WHERE asset_id=?", String.class, assetId)).isEqualTo("pending_review");
        assertThat(jdbc.queryForObject("SELECT reward_amount FROM consumer_campaign_reward WHERE asset_id=?", java.math.BigDecimal.class, assetId))
                .isEqualByComparingTo("80");

        mvc.perform(put("/api/creative/ai/consumer-assets/{id}/review", assetId)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType("application/json")
                        .content("{\"status\":\"approved\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM consumer_campaign_reward WHERE asset_id=?", String.class, assetId)).isEqualTo("rewarded");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_credit_transaction WHERE user_id=? AND asset_id=? AND biz_type='reward' AND amount=80", Integer.class, creator.id(), assetId))
                .isEqualTo(1);
    }

    private TestUser createUser(String username, String role) {
        jdbc.update("INSERT INTO user (username,password,role,status) VALUES (?,?,?,?)", username, "test-password", role, "active");
        Long id = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
        User user = new User(id, username, 20, username + "@test.local", null);
        user.setRole(role);
        return new TestUser(id, jwtService.issue(user));
    }

    private record TestUser(long id, String token) { }
}
