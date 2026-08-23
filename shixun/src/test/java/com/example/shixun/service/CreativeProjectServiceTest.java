package com.example.shixun.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CreativeProjectServiceTest {
    private JdbcTemplate jdbc;
    private CreativeProjectService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:project_" + System.nanoTime() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE creative_project (id BIGINT AUTO_INCREMENT PRIMARY KEY, project_no VARCHAR(120), user_id BIGINT, tenant_id BIGINT, name VARCHAR(180), theme VARCHAR(300), status VARCHAR(40), current_phase VARCHAR(40), current_version_id BIGINT, next_action VARCHAR(160), metadata_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_project_version (id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT, version_no VARCHAR(100), version_number INT, version_label VARCHAR(160), phase VARCHAR(40), status VARCHAR(30), frozen_at TIMESTAMP NULL, frozen_by BIGINT NULL, freeze_reason VARCHAR(500), freeze_hash VARCHAR(128), brief_json CLOB, metadata_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_project_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT, version_id BIGINT, user_id BIGINT, event_type VARCHAR(60), from_phase VARCHAR(40), to_phase VARCHAR(40), next_action VARCHAR(160), actor_type VARCHAR(30), actor_id BIGINT, idempotency_key VARCHAR(120), payload_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_conversation_session (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_no VARCHAR(80), user_id BIGINT, project_id BIGINT, version_id BIGINT, mode VARCHAR(24), status VARCHAR(24), updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_conversation_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT, user_id BIGINT, project_id BIGINT, version_id BIGINT, payload_json CLOB)");
        service = new CreativeProjectService(jdbc, new ObjectMapper());
    }

    @Test
    void createsVersionsAndDeduplicatesTimelineEvents() {
        Map<String, Object> project = service.createProject(7L, "图片灵感", "凤凰摆件", Map.of("mode", "image"));
        long projectId = ((Number) project.get("id")).longValue();
        long versionId = ((Number) project.get("currentVersionId")).longValue();
        assertThat(project.get("currentPhase")).isEqualTo("brief");
        assertThat(service.timeline(projectId, 7L)).hasSize(1);

        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("productType", "毛绒玩具");
        Map<String, Object> version = service.createVersion(projectId, 7L, Map.of(
                "versionLabel", "毛绒方案", "phase", "generation", "brief", brief));
        assertThat(version.get("versionNumber")).isEqualTo(2);
        assertThat(version.get("id")).isNotEqualTo(versionId);

        long createdVersionId = ((Number) version.get("id")).longValue();
        service.appendEvent(projectId, 7L, createdVersionId,
                "candidate_selected", "generation", "candidate_selected", "user", 7L, "candidate-1", brief);
        service.appendEvent(projectId, 7L, createdVersionId,
                "candidate_selected", "generation", "candidate_selected", "user", 7L, "candidate-1", brief);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_project_event WHERE project_id=?", Integer.class, projectId)).isEqualTo(3);
    }

    @Test
    void lazilyLinksLegacyConversationWithoutChangingItsEvents() {
        jdbc.update("INSERT INTO creative_conversation_session(id,session_no,user_id,mode,status) VALUES (11,'CCS-old',7,'image','draft')");
        jdbc.update("INSERT INTO creative_conversation_event(id,session_id,user_id,payload_json) VALUES (21,11,7,'{}')");

        CreativeProjectService.ProjectRef ref = service.ensureForSession(11L, 7L, "image");

        assertThat(jdbc.queryForObject("SELECT project_id FROM creative_conversation_session WHERE id=11", Long.class)).isEqualTo(ref.projectId());
        assertThat(jdbc.queryForObject("SELECT version_id FROM creative_conversation_event WHERE id=21", Long.class)).isEqualTo(ref.versionId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_project_event WHERE project_id=? AND event_type='project_created'", Integer.class, ref.projectId())).isEqualTo(1);
    }

    @Test
    void enforcesCanonicalTransitionsAndFreezesProductionSnapshot() {
        Map<String, Object> project = service.createProject(7L, "状态机测试", "明信片", Map.of());
        long projectId = ((Number) project.get("id")).longValue();
        long versionId = ((Number) project.get("currentVersionId")).longValue();

        service.transitionProject(projectId, versionId, 7L, "generating", "generation_started", "user", 7L, Map.of());
        assertThat(jdbc.queryForObject("SELECT phase FROM creative_project_version WHERE id=?", String.class, versionId)).isEqualTo("generation");

        Map<String, Object> snapshot = service.createProductionSnapshot(projectId, versionId, 7L);
        assertThat(snapshot.get("versionId")).isEqualTo(versionId);
        assertThat(snapshot.get("freezeHash")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM creative_project_version WHERE id=?", String.class, versionId)).isEqualTo("frozen");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.transitionProject(projectId, versionId, 7L, "multiview", "candidate_selected", "user", 7L, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("冻结");
    }

    @Test
    void idempotentTransitionsAlsoFreezeAndCannotBypassFrozenVersion() {
        Map<String, Object> project = service.createProject(7L, "幂等状态机测试", "毛绒玩具", Map.of());
        long projectId = ((Number) project.get("id")).longValue();
        long versionId = ((Number) project.get("currentVersionId")).longValue();

        service.transitionProject(projectId, versionId, 7L, "generation", "generation_started",
                "user", 7L, Map.of(), "generation-1");
        service.transitionProject(projectId, versionId, 7L, "multiview", "multiview_started",
                "user", 7L, Map.of(), "multiview-1");
        service.transitionProject(projectId, versionId, 7L, "preflight", "preflight_started",
                "system", 0L, Map.of(), "preflight-1");
        service.transitionProject(projectId, versionId, 7L, "ai_review", "ai_review_started",
                "system", 0L, Map.of(), "ai-review-1");
        service.transitionProject(projectId, versionId, 7L, "human_review", "human_review_started",
                "staff", 99L, Map.of(), "human-review-1");
        Map<String, Object> first = service.transitionProject(projectId, versionId, 7L, "approved",
                "human_review_approved", "staff", 99L, Map.of(), "approval-1");
        Map<String, Object> retry = service.transitionProject(projectId, versionId, 7L, "approved",
                "human_review_approved", "staff", 99L, Map.of("retry", true), "approval-1");

        assertThat(retry.get("id")).isEqualTo(first.get("id"));
        assertThat(jdbc.queryForObject("SELECT status FROM creative_project_version WHERE id=?", String.class, versionId))
                .isEqualTo("frozen");
        assertThat(jdbc.queryForObject("SELECT frozen_at FROM creative_project_version WHERE id=?", Object.class, versionId))
                .isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.transitionProject(projectId, versionId, 7L,
                        "generation", "retry_after_freeze", "user", 7L, Map.of(), "generation-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("冻结");
    }

    @Test
    void allowsApprovedProductionRequestAfterPreflightWasRunEarly() {
        Map<String, Object> project = service.createProject(7L, "预检后打样", "文创摆件", Map.of());
        long projectId = ((Number) project.get("id")).longValue();
        long versionId = ((Number) project.get("currentVersionId")).longValue();

        service.transitionProject(projectId, versionId, 7L, "generation", "generation_started",
                "user", 7L, Map.of());
        service.transitionProject(projectId, versionId, 7L, "preflight", "preflight_completed",
                "system", 0L, Map.of());
        service.transitionProject(projectId, versionId, 7L, "sampling", "sampling_requested",
                "user", 7L, Map.of());

        assertThat(jdbc.queryForObject("SELECT phase FROM creative_project_version WHERE id=?", String.class, versionId))
                .isEqualTo("sampling");
    }

    @Test
    void allowsFrozenSampleToReturnFromRevisionToAcceptance() {
        Map<String, Object> project = service.createProject(7L, "返修闭环", "文创摆件", Map.of());
        long projectId = ((Number) project.get("id")).longValue();
        long versionId = ((Number) project.get("currentVersionId")).longValue();

        service.transitionProject(projectId, versionId, 7L, "generation", "generation_started", "user", 7L, Map.of());
        service.transitionProject(projectId, versionId, 7L, "sampling", "sampling_requested", "user", 7L, Map.of());
        service.transitionProject(projectId, versionId, 7L, "needs_revision", "sample_revision_requested", "user", 7L, Map.of());
        service.transitionProject(projectId, versionId, 7L, "sampling", "sample_revision_completed", "staff", 9L, Map.of());
        service.transitionProject(projectId, versionId, 7L, "sample_accepted", "sample_accepted", "user", 7L, Map.of());

        assertThat(jdbc.queryForObject("SELECT phase FROM creative_project_version WHERE id=?", String.class, versionId))
                .isEqualTo("sample_accepted");
    }
}
