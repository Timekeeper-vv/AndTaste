package com.example.shixun.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreativeWorkflowDetailServiceTest {
    private JdbcTemplate jdbc;
    private CreativeWorkflowDetailService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:workflow_detail_" + System.nanoTime() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE consumer_production_request (id BIGINT PRIMARY KEY, request_no VARCHAR(80), user_id BIGINT, asset_id BIGINT, multiview_bundle_id BIGINT, request_type VARCHAR(20), title VARCHAR(200), quantity INT, status VARCHAR(30), review_comment VARCHAR(1000), reviewed_by VARCHAR(80), reviewed_at TIMESTAMP NULL, sample_product_name VARCHAR(120), sample_fee_yuan DECIMAL(10,2), sample_payment_status VARCHAR(24), sample_payment_order_no VARCHAR(64), sample_paid_at TIMESTAMP NULL, sample_workflow_status VARCHAR(32), sample_received_at TIMESTAMP NULL, sample_accepted_at TIMESTAMP NULL, sample_revision_count INT, bulk_unlocked_at TIMESTAMP NULL, bulk_unlocked_by BIGINT, project_id BIGINT, version_id BIGINT, version_snapshot_json CLOB, version_snapshot_hash VARCHAR(128), version_frozen_at TIMESTAMP NULL, note VARCHAR(1000), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_project (id BIGINT PRIMARY KEY, project_no VARCHAR(80), user_id BIGINT, name VARCHAR(180), theme VARCHAR(300), status VARCHAR(30), current_phase VARCHAR(40), current_version_id BIGINT, next_action VARCHAR(160), metadata_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_project_version (id BIGINT PRIMARY KEY, project_id BIGINT, version_no VARCHAR(80), version_number INT, version_label VARCHAR(160), phase VARCHAR(40), status VARCHAR(30), frozen_at TIMESTAMP NULL, frozen_by BIGINT, freeze_reason VARCHAR(500), freeze_hash VARCHAR(128), brief_json CLOB, metadata_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_project_event (id BIGINT PRIMARY KEY, project_id BIGINT, version_id BIGINT, event_type VARCHAR(60), from_phase VARCHAR(40), to_phase VARCHAR(40), next_action VARCHAR(160), actor_type VARCHAR(30), actor_id BIGINT, payload_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_preflight_report (id BIGINT PRIMARY KEY, project_id BIGINT, version_id BIGINT, status VARCHAR(24), score INT, version_freeze_hash VARCHAR(128), checks_json CLOB, issues_json CLOB, suggestions_json CLOB, context_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE design_review (id BIGINT PRIMARY KEY, review_no VARCHAR(80), asset_id BIGINT, overall_score DECIMAL(5,2), summary VARCHAR(1000), recommendation VARCHAR(50), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE payment_order (order_no VARCHAR(80) PRIMARY KEY, status VARCHAR(30), channel VARCHAR(40), amount_fen BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, paid_at TIMESTAMP NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_sample_lifecycle_event (id BIGINT PRIMARY KEY, request_id BIGINT, event_type VARCHAR(40), decision VARCHAR(32), rating INT, comment VARCHAR(2000), issue_tags_json CLOB, evidence_asset_ids_json CLOB, payload_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_sample_logistics (id BIGINT PRIMARY KEY, request_id BIGINT, carrier_code VARCHAR(50), carrier_name VARCHAR(80), tracking_no VARCHAR(120), status VARCHAR(30), latest_trace VARCHAR(1000), alert_level VARCHAR(20), alert_status VARCHAR(20), exception_note VARCHAR(2000), shipped_at TIMESTAMP NULL, signed_at TIMESTAMP NULL, estimated_arrival TIMESTAMP NULL, last_synced_at TIMESTAMP NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_sample_logistics_event (id BIGINT PRIMARY KEY, logistics_id BIGINT, request_id BIGINT, event_type VARCHAR(40), status VARCHAR(30), alert_level VARCHAR(20), location VARCHAR(160), content VARCHAR(1000), payload_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO creative_project (id,project_no,user_id,name,status,current_phase,current_version_id,next_action) VALUES (10,'CP-10',7,'凤凰抱枕','planning','human_review',100,'等待平台审核')");
        jdbc.update("INSERT INTO creative_project_version (id,project_id,version_no,version_number,version_label,phase,status) VALUES (100,10,'CP-10-V1',1,'初稿','human_review','frozen')");
        jdbc.update("INSERT INTO consumer_production_request (id,request_no,user_id,asset_id,request_type,title,quantity,status,sample_product_name,sample_fee_yuan,sample_payment_status,sample_workflow_status,project_id,version_id,sample_revision_count) VALUES (1,'CYP-1',7,20,'sample','凤凰抱枕打样',1,'review','抱枕',2000,'not_required','not_started',10,100,0)");
        service = new CreativeWorkflowDetailService(jdbc, new ObjectMapper());
    }

    @Test
    void exposesReviewBlockerAndNextActionFromOneReadModel() {
        Map<String, Object> detail = service.forConsumer(1L, 7L);
        Map<?, ?> flow = (Map<?, ?>) detail.get("flow");
        assertThat(flow.get("code")).isEqualTo("human_review");
        assertThat(flow.get("nextActionCode")).isEqualTo("wait");
        java.util.List<?> blockers = (java.util.List<?>) flow.get("blockers");
        java.util.List<String> blockerCodes = blockers.stream().map(item -> String.valueOf(((Map<?, ?>) item).get("code"))).toList();
        assertThat(blockerCodes)
                .contains("review");
    }

    @Test
    void switchesToPaymentAndFeedbackWithoutChangingHistoricalRequest() {
        jdbc.update("UPDATE consumer_production_request SET status='approved',sample_payment_status='unpaid' WHERE id=1");
        Map<?, ?> payment = (Map<?, ?>) service.forConsumer(1L, 7L).get("flow");
        assertThat(payment.get("code")).isEqualTo("payment_pending");
        assertThat(payment.get("nextActionCode")).isEqualTo("pay_sample");

        jdbc.update("UPDATE consumer_production_request SET status='shipped',sample_payment_status='paid',sample_workflow_status='shipped' WHERE id=1");
        jdbc.update("INSERT INTO creative_sample_lifecycle_event (id,request_id,event_type,comment) VALUES (1,1,'sample_shipped','已寄出')");
        Map<?, ?> feedback = (Map<?, ?>) service.forConsumer(1L, 7L).get("flow");
        assertThat(feedback.get("code")).isEqualTo("sample_shipped");
        assertThat(feedback.get("nextActionCode")).isEqualTo("submit_feedback");
        assertThat(((Map<?, ?>) service.forConsumer(1L, 7L).get("sample")).get("count")).isEqualTo(1);
    }

    @Test
    void consumerCannotReadAnotherUsersRequest() {
        assertThatThrownBy(() -> service.forConsumer(1L, 99L))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void consumerCannotReadTimelineFromAProjectOwnedByAnotherUser() {
        jdbc.update("INSERT INTO creative_project (id,project_no,user_id,name,status,current_phase,current_version_id,next_action) VALUES (11,'CP-11',99,'他人项目','planning','human_review',101,'等待审核')");
        jdbc.update("INSERT INTO creative_project_version (id,project_id,version_no,version_number,version_label,phase,status) VALUES (101,11,'CP-11-V1',1,'初稿','human_review','frozen')");
        jdbc.update("UPDATE consumer_production_request SET project_id=11,version_id=101 WHERE id=1");

        assertThatThrownBy(() -> service.forConsumer(1L, 7L))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("无权访问");
    }
}
