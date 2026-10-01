package com.example.shixun.controller;

import com.example.shixun.model.User;
import com.example.shixun.security.JwtService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfessionalChannelRequestIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:professional_channel_request;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void createSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS consumer_professional_submission ("
                + "id BIGINT PRIMARY KEY,submission_no VARCHAR(80),product_no VARCHAR(80),user_id BIGINT,"
                + "title VARCHAR(200),original_name VARCHAR(260),file_size BIGINT,purpose VARCHAR(30),"
                + "museum_id VARCHAR(80),museum_name VARCHAR(200),channel_request_status VARCHAR(24) DEFAULT 'not_requested',"
                + "channel_requested_at TIMESTAMP,museum_review_status VARCHAR(24) DEFAULT 'not_started',museum_review_comment VARCHAR(1000),museum_reviewed_at TIMESTAMP,note VARCHAR(1200),status VARCHAR(30),review_comment VARCHAR(1000),"
                + "resubmission_count INT DEFAULT 0,quoted_sample_fee_yuan DECIMAL(12,2),quoted_sample_lead_time VARCHAR(120),"
                + "quoted_sample_note VARCHAR(1200),sample_quantity INT,recipient_name VARCHAR(120),"
                + "recipient_phone VARCHAR(40),recipient_address VARCHAR(500),sample_payment_status VARCHAR(30),"
                + "sample_payment_order_no VARCHAR(80),sample_paid_at TIMESTAMP,created_at TIMESTAMP)");
        jdbc.update("DELETE FROM consumer_professional_submission");
        jdbc.update("DELETE FROM user");
    }

    @Test
    void ownerCanRequestOnlyAfterPlatformApprovalAndCanRetryWithoutDuplicates() throws Exception {
        String owner = token("channel-owner");
        String other = token("channel-other");
        jdbc.update("INSERT INTO consumer_professional_submission (id,submission_no,user_id,title,original_name,file_size,purpose,museum_name,status,created_at) "
                        + "VALUES (1,'CPS-1',(SELECT id FROM user WHERE username='channel-owner'),'作品','work.zip',42,'museum_sale','中国国家博物馆','review',CURRENT_TIMESTAMP)");

        mvc.perform(post("/api/creative/ai/consumer-professional-submissions/1/channel-request")
                .header("Authorization", "Bearer " + owner)).andExpect(status().isConflict());
        mvc.perform(post("/api/creative/ai/consumer-professional-submissions/1/channel-request")
                .header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());

        jdbc.update("UPDATE consumer_professional_submission SET status='approved' WHERE id=1");
        mvc.perform(post("/api/creative/ai/consumer-professional-submissions/1/channel-request")
                .header("Authorization", "Bearer " + owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("requested"))
                .andExpect(jsonPath("$.museumReviewStatus").value("review"));
        assertThat(jdbc.queryForObject("SELECT museum_review_status FROM consumer_professional_submission WHERE id=1", String.class)).isEqualTo("review");
        assertThat(jdbc.queryForObject("SELECT channel_requested_at FROM consumer_professional_submission WHERE id=1", Object.class)).isNotNull();
        mvc.perform(post("/api/creative/ai/consumer-professional-submissions/1/channel-request")
                .header("Authorization", "Bearer " + owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("requested"));
        mvc.perform(get("/api/creative/ai/consumer-professional-submissions/my")
                .header("Authorization", "Bearer " + owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].CHANNELREQUESTSTATUS").value("requested"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_professional_submission WHERE channel_request_status='requested'", Integer.class)).isEqualTo(1);

        mvc.perform(put("/api/creative/ai/consumer-professional-submissions/1/museum-review")
                .header("Authorization", "Bearer " + owner)
                .contentType("application/json").content("{\"status\":\"approved\"}"))
                .andExpect(status().isForbidden());
        String admin = token("channel-admin", "admin");
        mvc.perform(put("/api/creative/ai/consumer-professional-submissions/1/museum-review")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"status\":\"approved\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("approved"));
        assertThat(jdbc.queryForObject("SELECT museum_review_status FROM consumer_professional_submission WHERE id=1", String.class)).isEqualTo("approved");

        mvc.perform(put("/api/creative/ai/consumer-professional-submissions/1/museum-review")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"status\":\"rejected\",\"comment\":\"主题不符合本期征集方向\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("rejected"));
        mvc.perform(put("/api/creative/ai/consumer-professional-submissions/1/channel")
                .header("Authorization", "Bearer " + owner)
                .contentType("application/json").content("{\"museumId\":\"2\",\"museumName\":\"故宫博物院\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.museumName").value("故宫博物院"));
        assertThat(jdbc.queryForMap("SELECT museum_name,museum_review_status,channel_request_status FROM consumer_professional_submission WHERE id=1"))
                .containsEntry("MUSEUM_NAME", "故宫博物院")
                .containsEntry("MUSEUM_REVIEW_STATUS", "not_started")
                .containsEntry("CHANNEL_REQUEST_STATUS", "not_requested");
    }

    private String token(String username) {
        return token(username, "user");
    }

    private String token(String username, String role) {
        jdbc.update("INSERT INTO user (username,password,role,status) VALUES (?,?,?,?)", username, "test-password", role, "active");
        long id = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
        User user = new User(id, username, 20, username + "@test.local", null);
        user.setRole(role);
        return jwtService.issue(user);
    }
}
