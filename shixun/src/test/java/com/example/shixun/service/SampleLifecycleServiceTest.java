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

class SampleLifecycleServiceTest {
    private JdbcTemplate jdbc;
    private SampleLifecycleService lifecycle;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:sample_lifecycle_" + System.nanoTime() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE consumer_production_request (id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(80), user_id BIGINT, asset_id BIGINT NULL, multiview_bundle_id BIGINT NULL, title VARCHAR(200) NULL, request_type VARCHAR(20), status VARCHAR(30), sample_payment_status VARCHAR(24) DEFAULT 'not_required', sample_workflow_status VARCHAR(32) DEFAULT 'not_started', sample_received_at TIMESTAMP NULL, sample_accepted_at TIMESTAMP NULL, sample_revision_count INT DEFAULT 0, bulk_unlocked_at TIMESTAMP NULL, bulk_unlocked_by BIGINT NULL, project_id BIGINT NULL, version_id BIGINT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_sample_lifecycle_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT, project_id BIGINT NULL, version_id BIGINT NULL, user_id BIGINT, event_type VARCHAR(32), decision VARCHAR(32), rating INT NULL, comment VARCHAR(2000), issue_tags_json CLOB, evidence_asset_ids_json CLOB, payload_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        CreativeProjectService projects = new CreativeProjectService(jdbc, new ObjectMapper());
        lifecycle = new SampleLifecycleService(jdbc, new ObjectMapper(), projects);
        jdbc.update("INSERT INTO consumer_production_request (request_no,user_id,request_type,status,sample_workflow_status) VALUES ('CYP-1',7,'sample','approved','not_started')");
    }

    @Test
    void feedbackRevisionThenAcceptanceUnlocksBulk() {
        jdbc.update("UPDATE consumer_production_request SET status='shipped',sample_payment_status='not_required',sample_workflow_status='shipped' WHERE id=1");
        Map<String, Object> revision = lifecycle.submitFeedback(1L, 7L, Map.of(
                "decision", "revision_required", "comment", "缝线需要调整", "issueTags", List.of("工艺"), "rating", 3));
        assertThat(revision.get("sampleWorkflowStatus")).isEqualTo("revision_required");
        assertThat(jdbc.queryForObject("SELECT sample_revision_count FROM consumer_production_request WHERE id=1", Integer.class)).isEqualTo(1);

        // A new feedback is accepted only after the factory has sent the
        // revised sample again.
        jdbc.update("UPDATE consumer_production_request SET sample_workflow_status='shipped' WHERE id=1");
        Map<String, Object> accepted = lifecycle.acceptSample(1L, 7L, Map.of("comment", "已按要求修复", "rating", 5));
        assertThat(accepted.get("sampleWorkflowStatus")).isEqualTo("accepted");
        assertThat(jdbc.queryForObject("SELECT sample_accepted_at IS NOT NULL FROM consumer_production_request WHERE id=1", Boolean.class)).isTrue();

        Map<String, Object> unlocked = lifecycle.unlockBulk(1L, 7L, Map.of("comment", "同意量产"));
        assertThat(unlocked.get("sampleWorkflowStatus")).isEqualTo("bulk_unlocked");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_sample_lifecycle_event WHERE request_id=1", Integer.class)).isEqualTo(3);
    }

    @Test
    void revisionEndpointIncrementsRevisionCount() {
        jdbc.update("UPDATE consumer_production_request SET status='shipped',sample_payment_status='not_required',sample_workflow_status='shipped' WHERE id=1");
        lifecycle.requestRevision(1L, 7L, Map.of("comment", "请重新调整尺寸"));
        assertThat(jdbc.queryForObject("SELECT sample_revision_count FROM consumer_production_request WHERE id=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT event_type FROM creative_sample_lifecycle_event WHERE request_id=1", String.class)).isEqualTo("revision_requested");
    }

    @Test
    void cannotGiveFeedbackBeforePaidSampleStartsOrAfterBulkUnlock() {
        jdbc.update("UPDATE consumer_production_request SET status='approved',sample_payment_status='unpaid' WHERE id=1");
        assertThatThrownBy(() -> lifecycle.submitFeedback(1L, 7L,
                Map.of("decision", "revision_required", "comment", "尚未收到样品")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未进入生产流程");

        jdbc.update("UPDATE consumer_production_request SET status='processing',sample_payment_status='paid',sample_workflow_status='in_production' WHERE id=1");
        assertThatThrownBy(() -> lifecycle.acceptSample(1L, 7L, Map.of("comment", "样品还在制作")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未寄出");

        jdbc.update("UPDATE consumer_production_request SET status='shipped',sample_workflow_status='shipped' WHERE id=1");
        lifecycle.acceptSample(1L, 7L, Map.of("comment", "验收通过"));
        lifecycle.unlockBulk(1L, 7L, Map.of());
        assertThatThrownBy(() -> lifecycle.requestRevision(1L, 7L, Map.of("comment", "重复返修")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生命周期已结束");
    }

    @Test
    void freeSampleCanBeAcceptedAfterFactoryShipsIt() {
        jdbc.update("UPDATE consumer_production_request SET status='shipped',sample_payment_status='not_required',sample_workflow_status='shipped' WHERE id=1");
        Map<String, Object> accepted = lifecycle.acceptSample(1L, 7L, Map.of("comment", "免费样品验收通过"));
        assertThat(accepted.get("sampleWorkflowStatus")).isEqualTo("accepted");
    }

    @Test
    void freeSampleCanGiveFeedbackAfterFactoryShipsIt() {
        jdbc.update("UPDATE consumer_production_request SET status='shipped',sample_payment_status='not_required',sample_workflow_status='shipped' WHERE id=1");
        Map<String, Object> result = lifecycle.submitFeedback(1L, 7L,
                Map.of("decision", "revision_required", "comment", "收到后需要调整颜色"));
        assertThat(result.get("sampleWorkflowStatus")).isEqualTo("revision_required");
    }

    @Test
    void genericAcceptFeedbackRecordsAcceptanceTimestamp() {
        jdbc.update("UPDATE consumer_production_request SET status='shipped',sample_payment_status='not_required',sample_workflow_status='shipped' WHERE id=1");
        Map<String, Object> result = lifecycle.submitFeedback(1L, 7L,
                Map.of("decision", "accept", "comment", "通用反馈入口验收通过"));
        assertThat(result.get("sampleWorkflowStatus")).isEqualTo("accepted");
        assertThat(jdbc.queryForObject("SELECT sample_accepted_at IS NOT NULL FROM consumer_production_request WHERE id=1", Boolean.class)).isTrue();
    }

    @Test
    void cannotAcceptOrRequestAnotherRevisionWhileFactoryIsReworking() {
        jdbc.update("UPDATE consumer_production_request SET status='shipped',sample_payment_status='not_required',sample_workflow_status='shipped' WHERE id=1");
        lifecycle.requestRevision(1L, 7L, Map.of("comment", "请调整颜色"));

        assertThatThrownBy(() -> lifecycle.acceptSample(1L, 7L, Map.of("comment", "先验收")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未寄出");
        assertThatThrownBy(() -> lifecycle.requestRevision(1L, 7L, Map.of("comment", "重复返修")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未寄出");
    }
}
