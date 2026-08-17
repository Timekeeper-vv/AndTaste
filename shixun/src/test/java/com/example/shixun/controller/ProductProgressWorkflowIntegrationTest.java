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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the consumer path used by the mini-program product-progress page.
 * The test calls the authenticated HTTP endpoints and checks the durable rows
 * rather than relying only on client-side optimistic state.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductProgressWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;

    @DynamicPropertySource
    static void workflowProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:product_progress_workflow;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void resetData() {
        createWorkflowSchema();
        jdbc.update("DELETE FROM consumer_production_request");
        jdbc.update("DELETE FROM consumer_sample_fee_catalog");
        jdbc.update("DELETE FROM commercial_application_audit_log");
        jdbc.update("DELETE FROM creative_consignment_application");
        jdbc.update("DELETE FROM creative_quote_request");
        jdbc.update("DELETE FROM selection_demand_request");
        jdbc.update("DELETE FROM creative_product_template");
        jdbc.update("DELETE FROM user_selection_favorite");
        jdbc.update("DELETE FROM selection_option");
        jdbc.update("DELETE FROM selection_category");
        jdbc.update("DELETE FROM channel_directory");
        jdbc.update("DELETE FROM digital_asset");
        jdbc.update("DELETE FROM user");
    }

    @Test
    void submissionsCreateDurableProgressRecordsForOnlyTheSubmittingAccount() throws Exception {
        TestUser creator = createUser("workflow-creator", "user");
        TestUser otherConsumer = createUser("workflow-other", "user");
        TestUser reviewer = createUser("workflow-reviewer", "admin");
        seedProductCatalog();

        long imageAssetId = createAsset(creator, "AST-WORKFLOW-IMAGE", "image", "draft");

        JsonNode imageReview = request(put("/api/creative/ai/consumer-assets/{id}/submit-review", imageAssetId), creator.token(),
                Map.of("purpose", "personal"));
        assertThat(imageReview.path("success").asBoolean()).isTrue();
        assertThat(imageReview.path("status").asText()).isEqualTo("review");
        assertThat(assetStatus(imageAssetId)).isEqualTo("review");

        JsonNode creatorAssets = request(get("/api/creative/ai/assets"), creator.token(), null);
        assertThat(containsId(creatorAssets, imageAssetId)).isTrue();
        assertThat(field(assetById(creatorAssets, imageAssetId), "status").asText()).isEqualTo("review");
        JsonNode otherAssets = request(get("/api/creative/ai/assets"), otherConsumer.token(), null);
        assertThat(containsId(otherAssets, imageAssetId)).isFalse();

        reviewAsset(reviewer, imageAssetId, "approved");
        assertThat(assetStatus(imageAssetId)).isEqualTo("approved");

        JsonNode demand = request(post("/api/selection/demands"), creator.token(), Map.of(
                "optionKey", "souvenir-alloy-magnet",
                "assetId", imageAssetId,
                "theme", "青铜云纹",
                "audience", "文博游客",
                "occasion", "旅行伴手礼"));
        String demandNo = demand.path("requestNo").asText();
        assertThat(demandNo).startsWith("SDR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM selection_demand_request WHERE request_no=? AND user_id=?", Integer.class, demandNo, creator.id()))
                .isEqualTo(1);

        JsonNode quote = request(post("/api/commercial/consumer/quote-requests"), creator.token(), Map.of(
                "templateCode", "alloy-magnet",
                "assetId", imageAssetId,
                "requestType", "sample",
                "quantity", 1,
                "purpose", "personal",
                "copyrightBasis", "original",
                "copyrightConfirmed", true));
        String quoteNo = quote.path("requestNo").asText();
        assertThat(quote.path("requestKind").asText()).isEqualTo("quote");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_quote_request WHERE request_no=? AND user_id=?", Integer.class, quoteNo, creator.id()))
                .isEqualTo(1);

        JsonNode consignment = request(post("/api/commercial/consumer/consignment-applications"), creator.token(), Map.of(
                "templateCode", "alloy-magnet",
                "assetId", imageAssetId,
                "channelId", 1,
                "copyrightBasis", "original",
                "copyrightConfirmed", true));
        String consignmentNo = consignment.path("requestNo").asText();
        assertThat(consignment.path("requestKind").asText()).isEqualTo("consignment");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_consignment_application WHERE application_no=? AND user_id=?", Integer.class, consignmentNo, creator.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commercial_application_audit_log WHERE operator=?", Integer.class, "workflow-creator"))
                .isEqualTo(2);

        long modelAssetId = createAsset(creator, "AST-WORKFLOW-MODEL", "model", "draft");
        request(put("/api/creative/ai/consumer-assets/{id}/submit-review", modelAssetId), creator.token(), Map.of("purpose", "personal"));
        reviewAsset(reviewer, modelAssetId, "approved");

        // The selected product is bound in the generated asset metadata. The
        // mini-program deliberately does not ask the user to select it again.
        JsonNode production = request(post("/api/creative/ai/consumer-production/submit"), creator.token(), Map.of(
                "assetId", modelAssetId,
                "requestType", "sample",
                "quantity", 1,
                "purpose", "personal",
                "recipientName", "测试用户",
                "recipientPhone", "13800138000",
                "recipientAddress", "北京市东城区测试路 1 号"));
        String productionNo = production.path("requestNo").asText();
        assertThat(production.path("success").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM consumer_production_request WHERE request_no=?", String.class, productionNo))
                .isEqualTo("review");
        assertThat(jdbc.queryForObject("SELECT sample_product_name FROM consumer_production_request WHERE request_no=?", String.class, productionNo))
                .isEqualTo("合金冰箱贴");
        assertThat(jdbc.queryForObject("SELECT sample_fee_yuan FROM consumer_production_request WHERE request_no=?", BigDecimal.class, productionNo))
                .isEqualByComparingTo("2000.00");

        JsonNode progress = request(get("/api/commercial/consumer/requests"), creator.token(), null);
        assertThat(progress.path("quoteRequests").size()).isEqualTo(1);
        assertThat(progress.path("consignmentApplications").size()).isEqualTo(1);
        assertThat(progress.path("selectionDemands").size()).isEqualTo(1);
        assertThat(field(progress.path("quoteRequests").get(0), "requestNo").asText()).isEqualTo(quoteNo);
        assertThat(field(progress.path("consignmentApplications").get(0), "applicationNo").asText()).isEqualTo(consignmentNo);
        assertThat(field(progress.path("selectionDemands").get(0), "requestNo").asText()).isEqualTo(demandNo);

        JsonNode productionRequests = request(get("/api/creative/ai/consumer-production/my"), creator.token(), null);
        assertThat(productionRequests.size()).isEqualTo(1);
        assertThat(field(productionRequests.get(0), "requestNo").asText()).isEqualTo(productionNo);
        assertThat(field(productionRequests.get(0), "sampleProductName").asText()).isEqualTo("合金冰箱贴");

        JsonNode otherProgress = request(get("/api/commercial/consumer/requests"), otherConsumer.token(), null);
        assertThat(otherProgress.path("quoteRequests").size()).isZero();
        assertThat(otherProgress.path("consignmentApplications").size()).isZero();
        assertThat(otherProgress.path("selectionDemands").size()).isZero();
        JsonNode otherProductionRequests = request(get("/api/creative/ai/consumer-production/my"), otherConsumer.token(), null);
        assertThat(otherProductionRequests.size()).isZero();
    }

    private JsonNode request(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                             String token, Map<String, Object> body) throws Exception {
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        String response = mvc.perform(builder.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(response);
    }

    private void reviewAsset(TestUser reviewer, long assetId, String statusValue) throws Exception {
        JsonNode response = request(put("/api/creative/ai/consumer-assets/{id}/review", assetId), reviewer.token(),
                Map.of("status", statusValue));
        assertThat(response.path("status").asText()).isEqualTo(statusValue);
    }

    private TestUser createUser(String username, String role) {
        jdbc.update("INSERT INTO user (username,password,role,status) VALUES (?,?,?,?)", username, "test-password", role, "active");
        long id = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
        User user = new User(id, username, 20, username + "@test.local", null);
        user.setRole(role);
        return new TestUser(id, jwtService.issue(user));
    }

    private void seedProductCatalog() {
        jdbc.update("INSERT INTO selection_category (category_key,name,description,review_status,enabled,sort_order) VALUES (?,?,?,?,?,?)",
                "souvenir", "文旅纪念", "测试用公开选品分类", "approved", 1, 1);
        jdbc.update("INSERT INTO selection_option (option_key,category_key,name,subtitle,description,material,process,specification,sample_lead_time,bulk_lead_time,retail_display,tags,audience_tags,occasion_tags,budget_band,review_status,enabled,sort_order) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "souvenir-alloy-magnet", "souvenir", "合金冰箱贴", "测试商品", "用于流程测试的合金冰箱贴", "锌合金", "压铸", "60mm", "7天", "15天", "待运营确认", "云纹", "游客", "伴手礼", "50-100", "approved", 1, 1);
        long optionId = jdbc.queryForObject("SELECT id FROM selection_option WHERE option_key='souvenir-alloy-magnet'", Long.class);
        jdbc.update("INSERT INTO creative_product_template (template_code,selection_option_id,product_name,published,supply_status) VALUES (?,?,?,?,?)",
                "alloy-magnet", optionId, "合金冰箱贴", 1, "confirmed");
        jdbc.update("INSERT INTO channel_directory (id,channel_code,name,province,city,district,channel_type,source_type,cooperation_status,enabled) VALUES (?,?,?,?,?,?,?,?,?,?)",
                1L, "museum-test", "测试博物馆", "北京市", "北京市", "东城区", "museum", "test", "directory_only", 1);
        jdbc.update("INSERT INTO consumer_sample_fee_catalog (product_name,fee_yuan,active) VALUES (?,?,?)", "合金冰箱贴", new BigDecimal("2000.00"), 1);
    }

    private long createAsset(TestUser owner, String assetNo, String assetType, String statusValue) {
        jdbc.update("INSERT INTO digital_asset (asset_no,title,asset_type,source_type,file_url,preview_url,prompt,metadata_json,format,status,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                assetNo, "之间智造效果图", assetType, "ai_generated", "/generated/test.png", "/generated/test.png", "青铜云纹合金冰箱贴",
                "{\"productKey\":\"souvenir-alloy-magnet\",\"productName\":\"合金冰箱贴\"}", assetType.equals("model") ? "glb" : "png", statusValue, owner.id());
        return jdbc.queryForObject("SELECT id FROM digital_asset WHERE asset_no=?", Long.class, assetNo);
    }

    private String assetStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM digital_asset WHERE id=?", String.class, id);
    }

    private boolean containsId(JsonNode nodes, long id) {
        return assetById(nodes, id) != null;
    }

    private JsonNode assetById(JsonNode nodes, long id) {
        for (JsonNode node : nodes) if (field(node, "id").asLong() == id) return node;
        return null;
    }

    private JsonNode field(JsonNode row, String name) {
        JsonNode exact = row.get(name);
        if (exact != null) return exact;
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = row.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return mapper.nullNode();
    }

    private void createWorkflowSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS selection_category (id BIGINT AUTO_INCREMENT PRIMARY KEY,category_key VARCHAR(60) NOT NULL UNIQUE,name VARCHAR(80) NOT NULL,description VARCHAR(300) NOT NULL,review_status VARCHAR(30) NOT NULL,effective_from DATE,enabled TINYINT NOT NULL DEFAULT 1,sort_order INT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS selection_option (id BIGINT AUTO_INCREMENT PRIMARY KEY,option_key VARCHAR(80) NOT NULL UNIQUE,category_key VARCHAR(60) NOT NULL,name VARCHAR(120) NOT NULL,subtitle VARCHAR(200) NOT NULL,description VARCHAR(500) NOT NULL,material VARCHAR(500) NOT NULL,process VARCHAR(1000) NOT NULL,specification VARCHAR(500) NOT NULL,sample_lead_time VARCHAR(60) NOT NULL,bulk_lead_time VARCHAR(60) NOT NULL,retail_min DECIMAL(12,2),retail_max DECIMAL(12,2),retail_display VARCHAR(80) NOT NULL,tags VARCHAR(1000) NOT NULL,audience_tags VARCHAR(500) NOT NULL,occasion_tags VARCHAR(500) NOT NULL,budget_band VARCHAR(30) NOT NULL,cover_image_url VARCHAR(500),image_source VARCHAR(500),image_rights_status VARCHAR(40),source_version VARCHAR(40),source_name VARCHAR(200),source_page INT,review_status VARCHAR(30) NOT NULL,effective_from DATE,enabled TINYINT NOT NULL DEFAULT 1,sort_order INT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_selection_favorite (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,option_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS selection_demand_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,request_no VARCHAR(80) NOT NULL UNIQUE,user_id BIGINT NOT NULL,option_id BIGINT NOT NULL,asset_id BIGINT,theme VARCHAR(300),budget_max DECIMAL(12,2),audience VARCHAR(200),occasion VARCHAR(100),note VARCHAR(1000),status VARCHAR(30) NOT NULL DEFAULT 'new',created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_product_template (id BIGINT AUTO_INCREMENT PRIMARY KEY,template_code VARCHAR(80) NOT NULL UNIQUE,selection_option_id BIGINT,product_name VARCHAR(160) NOT NULL,published TINYINT NOT NULL DEFAULT 1,supply_status VARCHAR(30) NOT NULL DEFAULT 'pending_confirmation')");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_quote_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,request_no VARCHAR(80) NOT NULL UNIQUE,user_id BIGINT NOT NULL,asset_id BIGINT,product_template_id BIGINT NOT NULL,request_type VARCHAR(30) NOT NULL,quantity INT NOT NULL,purpose VARCHAR(30) NOT NULL,note VARCHAR(1200),copyright_basis VARCHAR(30) NOT NULL,copyright_confirmed TINYINT NOT NULL,copyright_statement_version VARCHAR(30) NOT NULL,status VARCHAR(30) NOT NULL DEFAULT 'new',quoted_unit_price DECIMAL(10,2),quoted_total_price DECIMAL(12,2),quoted_lead_time VARCHAR(120),operator_comment VARCHAR(1200),sample_payment_status VARCHAR(24) NOT NULL DEFAULT 'not_required',sample_payment_order_no VARCHAR(64),sample_paid_at TIMESTAMP,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_consignment_application (id BIGINT AUTO_INCREMENT PRIMARY KEY,application_no VARCHAR(80) NOT NULL UNIQUE,user_id BIGINT NOT NULL,asset_id BIGINT NOT NULL,product_template_id BIGINT NOT NULL,channel_id BIGINT,channel_name_snapshot VARCHAR(200),sales_mode VARCHAR(30) NOT NULL,creator_share_percent DECIMAL(5,2) NOT NULL,platform_service_percent DECIMAL(5,2) NOT NULL,note VARCHAR(1200),copyright_basis VARCHAR(30) NOT NULL,copyright_confirmed TINYINT NOT NULL,copyright_statement_version VARCHAR(30) NOT NULL,authorization_note VARCHAR(1000),status VARCHAR(30) NOT NULL DEFAULT 'pending_review',operator_comment VARCHAR(1200),created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS commercial_application_audit_log (id BIGINT AUTO_INCREMENT PRIMARY KEY,application_type VARCHAR(30) NOT NULL,application_id BIGINT NOT NULL,action VARCHAR(40) NOT NULL,operator VARCHAR(80) NOT NULL,comment VARCHAR(1200),created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS consumer_sample_fee_catalog (id BIGINT AUTO_INCREMENT PRIMARY KEY,product_name VARCHAR(120) NOT NULL UNIQUE,fee_yuan DECIMAL(10,2) NOT NULL,active TINYINT NOT NULL DEFAULT 1)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS consumer_production_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,request_no VARCHAR(80) NOT NULL UNIQUE,user_id BIGINT NOT NULL,asset_id BIGINT NOT NULL,request_type VARCHAR(20) NOT NULL,title VARCHAR(200),quantity INT NOT NULL,self_ship_quantity INT NOT NULL,museum_distribution_json CLOB,recipient_name VARCHAR(80),recipient_phone VARCHAR(80),recipient_address VARCHAR(500),note VARCHAR(1000),status VARCHAR(30) NOT NULL DEFAULT 'review',review_comment VARCHAR(1000),reviewed_by VARCHAR(80),reviewed_at TIMESTAMP,sample_product_name VARCHAR(120),sample_fee_yuan DECIMAL(10,2),sample_payment_status VARCHAR(24) NOT NULL DEFAULT 'not_required',sample_payment_order_no VARCHAR(64),sample_paid_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP)");
    }

    private record TestUser(long id, String token) { }
}
