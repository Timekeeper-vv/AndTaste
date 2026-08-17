package com.example.shixun.controller;

import com.example.shixun.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommercialProductizationControllerTest {
    private JdbcTemplate jdbc;
    private CommercialProductizationController controller;
    private JwtService.Claims consumer;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:commercial_progress_" + System.nanoTime()
                        + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        seedData();
        controller = new CommercialProductizationController(jdbc);
        consumer = new JwtService.Claims(1L, "consumer", "user", Instant.now().getEpochSecond() + 900);
    }

    @Test
    void commercialRequestsReturnsCurrentUsersQuoteAndConsignmentRequests() {
        Map<String, Object> result = controller.consumerRequests(consumer);
        Map<String, Object> compatibilityResult = controller.consumerProductProgress(consumer);

        List<Map<String, Object>> quotes = rows(result, "quoteRequests");
        List<Map<String, Object>> consignments = rows(result, "consignmentApplications");
        List<Map<String, Object>> selectionDemands = rows(result, "selectionDemands");
        Map<String, Object> summary = map(result, "summary");

        assertThat(quotes).hasSize(2);
        assertThat(consignments).hasSize(1);
        assertThat(selectionDemands).hasSize(1);
        assertThat(selectionDemands.get(0).get("requestNo")).isEqualTo("SDR-001");
        assertThat(selectionDemands.get(0).get("productName")).isEqualTo("文化书签");
        assertThat(quotes).extracting(row -> String.valueOf(row.get("requestNo")))
                .containsExactly("CQR-002", "CQR-001");
        assertThat(quotes.get(0).get("productName")).isEqualTo("历史商品化申请");
        assertThat(quotes.get(0).get("templateCode")).isEqualTo("archived-product-999");
        assertThat(summary.get("quoteRequestCount")).isEqualTo(2);
        assertThat(summary.get("consignmentApplicationCount")).isEqualTo(1);
        assertThat(summary.get("selectionDemandCount")).isEqualTo(1);
        assertThat(result.get("syncedAt")).isNotNull();
        assertThat(rows(compatibilityResult, "quoteRequests")).hasSize(2);
        assertThat(rows(compatibilityResult, "consignmentApplications")).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> value, String key) {
        return (List<Map<String, Object>>) value.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Map<String, Object> value, String key) {
        return (Map<String, Object>) value.get(key);
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE user (id BIGINT PRIMARY KEY, username VARCHAR(100), role VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE creative_product_template (id BIGINT PRIMARY KEY, template_code VARCHAR(80), product_name VARCHAR(200))");
        jdbc.execute("CREATE TABLE creative_quote_request ("
                + "id BIGINT PRIMARY KEY,request_no VARCHAR(80),user_id BIGINT,asset_id BIGINT,product_template_id BIGINT,"
                + "request_type VARCHAR(30),quantity INT,purpose VARCHAR(30),status VARCHAR(30),"
                + "quoted_unit_price DECIMAL(10,2),quoted_total_price DECIMAL(12,2),quoted_lead_time VARCHAR(120),"
                + "operator_comment VARCHAR(1200),sample_payment_status VARCHAR(24),sample_payment_order_no VARCHAR(64),"
                + "sample_paid_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_consignment_application ("
                + "id BIGINT PRIMARY KEY,application_no VARCHAR(80),user_id BIGINT,asset_id BIGINT,product_template_id BIGINT,"
                + "channel_id BIGINT,channel_name_snapshot VARCHAR(200),sales_mode VARCHAR(30),"
                + "creator_share_percent DECIMAL(5,2),platform_service_percent DECIMAL(5,2),status VARCHAR(30),"
                + "operator_comment VARCHAR(1200),created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE selection_option (id BIGINT PRIMARY KEY, option_key VARCHAR(80), name VARCHAR(120))");
        jdbc.execute("CREATE TABLE selection_demand_request ("
                + "id BIGINT PRIMARY KEY,request_no VARCHAR(80),user_id BIGINT,option_id BIGINT,asset_id BIGINT,"
                + "theme VARCHAR(300),budget_max DECIMAL(12,2),audience VARCHAR(200),occasion VARCHAR(100),"
                + "note VARCHAR(1000),status VARCHAR(30),created_at TIMESTAMP,updated_at TIMESTAMP)");
    }

    private void seedData() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("INSERT INTO user(id,username,role,status) VALUES (1,'consumer','user','active'),(2,'other','user','active')");
        jdbc.update("INSERT INTO creative_product_template(id,template_code,product_name) VALUES (10,'bookmark','Museum bookmark')");
        jdbc.update("INSERT INTO creative_quote_request VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, "CQR-001", 1L, 101L, 10L, "sample", 1, "personal", "new",
                new BigDecimal("0"), new BigDecimal("0"), "", "", "not_required", null, null, now, now);
        jdbc.update("INSERT INTO creative_quote_request VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                2L, "CQR-002", 1L, 102L, 999L, "bulk", 50, "personal", "processing",
                null, null, null, null, "not_required", null, null, now, now);
        jdbc.update("INSERT INTO creative_quote_request VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                3L, "CQR-OTHER", 2L, 103L, 10L, "sample", 1, "personal", "new",
                null, null, null, null, "not_required", null, null, now, now);
        jdbc.update("INSERT INTO creative_consignment_application VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, "CCA-001", 1L, 101L, 10L, 5L, "Museum Store", "preorder",
                new BigDecimal("70"), new BigDecimal("30"), "pending_review", "", now, now);
        jdbc.update("INSERT INTO creative_consignment_application VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                2L, "CCA-OTHER", 2L, 103L, 10L, 5L, "Other Store", "preorder",
                new BigDecimal("70"), new BigDecimal("30"), "pending_review", "", now, now);
        jdbc.update("INSERT INTO selection_option VALUES (1,'stationery-metal-bookmark','文化书签'),(2,'stationery-postcard','明信片')");
        jdbc.update("INSERT INTO selection_demand_request VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, "SDR-001", 1L, 1L, 101L, "博物馆纹样", null, "游客", "伴手礼", "", "new", now, now);
        jdbc.update("INSERT INTO selection_demand_request VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                2L, "SDR-OTHER", 2L, 2L, 103L, "城市主题", null, "游客", "伴手礼", "", "new", now, now);
    }
}
