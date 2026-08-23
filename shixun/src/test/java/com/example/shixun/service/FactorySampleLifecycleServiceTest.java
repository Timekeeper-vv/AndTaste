package com.example.shixun.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FactorySampleLifecycleServiceTest {
    private JdbcTemplate jdbc;
    private FactorySampleLifecycleService lifecycle;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:factory_sample_" + System.nanoTime() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE user (id BIGINT PRIMARY KEY, username VARCHAR(80), role VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE digital_asset (id BIGINT PRIMARY KEY, title VARCHAR(200))");
        jdbc.execute("CREATE TABLE consumer_production_request (id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(80), user_id BIGINT, asset_id BIGINT, request_type VARCHAR(20), title VARCHAR(200), quantity INT, status VARCHAR(30), sample_product_name VARCHAR(120), sample_fee_yuan DECIMAL(10,2), sample_payment_status VARCHAR(24), sample_workflow_status VARCHAR(32) DEFAULT 'not_started', sample_received_at TIMESTAMP NULL, sample_accepted_at TIMESTAMP NULL, sample_revision_count INT DEFAULT 0, bulk_unlocked_at TIMESTAMP NULL, project_id BIGINT NULL, version_id BIGINT NULL, recipient_name VARCHAR(80), recipient_phone VARCHAR(40), recipient_address VARCHAR(255), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_sample_lifecycle_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT, project_id BIGINT NULL, version_id BIGINT NULL, user_id BIGINT, event_type VARCHAR(32), decision VARCHAR(32), rating INT NULL, comment VARCHAR(2000), issue_tags_json CLOB, evidence_asset_ids_json CLOB, payload_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO user (id,username,role,status) VALUES (7,'customer','user','active'),(9,'factory','feeder','active')");
        jdbc.update("INSERT INTO digital_asset (id,title) VALUES (4,'凤凰抱枕')");
        jdbc.update("INSERT INTO consumer_production_request (request_no,user_id,asset_id,request_type,title,quantity,status,sample_product_name,sample_payment_status,sample_workflow_status) VALUES ('CYP-1',7,4,'sample','凤凰抱枕打样',1,'approved','抱枕','not_required','not_started')");
        lifecycle = new FactorySampleLifecycleService(jdbc, new ObjectMapper());
    }

    @Test
    void factoryCanMoveSampleFromProductionToShipment() {
        Map<String, Object> production = lifecycle.updateStatus(1L, "in_production", 9L, "factory", "开始制作", List.of());
        assertThat(production.get("sampleWorkflowStatus")).isEqualTo("in_production");
        Map<String, Object> ready = lifecycle.updateStatus(1L, "ready_to_ship", 9L, "factory", "样品已出样", List.of());
        assertThat(ready.get("sampleWorkflowStatus")).isEqualTo("ready_to_ship");
        Map<String, Object> shipped = lifecycle.updateStatus(1L, "shipped", 9L, "factory", "顺丰 SF123", List.of());
        assertThat(shipped.get("sampleWorkflowStatus")).isEqualTo("shipped");
        assertThat(jdbc.queryForObject("SELECT status FROM consumer_production_request WHERE id=1", String.class)).isEqualTo("shipped");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_sample_lifecycle_event WHERE request_id=1", Integer.class)).isEqualTo(3);
    }

    @Test
    void revisionPathRequiresCustomerRevisionRequest() {
        jdbc.update("UPDATE consumer_production_request SET sample_workflow_status='revision_required' WHERE id=1");
        lifecycle.updateStatus(1L, "revision_in_progress", 9L, "factory", "返修开始", List.of());
        Map<String, Object> completed = lifecycle.updateStatus(1L, "revision_completed", 9L, "factory", "尺寸已修正", List.of());
        assertThat(completed.get("sampleWorkflowStatus")).isEqualTo("revision_completed");
        Map<String, Object> ready = lifecycle.updateStatus(1L, "ready_to_ship", 9L, "factory", "返修样品已出样", List.of());
        assertThat(ready.get("sampleWorkflowStatus")).isEqualTo("ready_to_ship");
        Map<String, Object> shipped = lifecycle.updateStatus(1L, "shipped", 9L, "factory", "返修样品已寄出", List.of());
        assertThat(shipped.get("sampleWorkflowStatus")).isEqualTo("shipped");
    }

    @Test
    void factoryCannotStartBeforeApprovalAndPayment() {
        jdbc.update("UPDATE consumer_production_request SET status='approved', sample_payment_status='unpaid' WHERE id=1");
        assertThatThrownBy(() -> lifecycle.updateStatus(1L, "in_production", 9L, "factory", "", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审核或付款");
    }

    @Test
    void repairedSampleMustPassReadyToShipCheckpointBeforeShipment() {
        jdbc.update("UPDATE consumer_production_request SET status='processing',sample_payment_status='not_required',sample_workflow_status='revision_completed' WHERE id=1");
        assertThatThrownBy(() -> lifecycle.updateStatus(1L, "shipped", 9L, "factory", "直接寄出", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能从");
    }
}
