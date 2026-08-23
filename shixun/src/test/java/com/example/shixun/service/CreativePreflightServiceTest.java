package com.example.shixun.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CreativePreflightServiceTest {
    private JdbcTemplate jdbc;
    private CreativePreflightService preflight;
    private long projectId;
    private long versionId;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:preflight_" + System.nanoTime()
                        + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE creative_project (id BIGINT AUTO_INCREMENT PRIMARY KEY, project_no VARCHAR(120), user_id BIGINT, name VARCHAR(180), theme VARCHAR(300), status VARCHAR(40), current_phase VARCHAR(40), current_version_id BIGINT, next_action VARCHAR(160), metadata_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_project_version (id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT, version_no VARCHAR(100), version_number INT, version_label VARCHAR(160), phase VARCHAR(40), status VARCHAR(30), frozen_at TIMESTAMP NULL, frozen_by BIGINT NULL, freeze_reason VARCHAR(500), freeze_hash VARCHAR(128), brief_json CLOB, metadata_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_project_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT, version_id BIGINT, user_id BIGINT, event_type VARCHAR(60), from_phase VARCHAR(40), to_phase VARCHAR(40), next_action VARCHAR(160), actor_type VARCHAR(30), actor_id BIGINT, idempotency_key VARCHAR(120), payload_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE digital_asset (id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_type VARCHAR(30), status VARCHAR(30), format VARCHAR(30), tags VARCHAR(500), metadata_json CLOB, prompt CLOB, negative_prompt CLOB, created_by BIGINT, project_id BIGINT, version_id BIGINT)");
        jdbc.execute("CREATE TABLE creative_multiview_bundle (id BIGINT AUTO_INCREMENT PRIMARY KEY, status VARCHAR(30), view_count INT, project_id BIGINT, version_id BIGINT, input_asset_id BIGINT, user_id BIGINT, product_name VARCHAR(180), material VARCHAR(180), product_size VARCHAR(120))");
        jdbc.execute("CREATE TABLE design_review (id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_id BIGINT, overall_score DECIMAL(5,2), recommendation VARCHAR(30))");
        jdbc.execute("CREATE TABLE creative_preflight_report (id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT, version_id BIGINT, user_id BIGINT, status VARCHAR(24), score INT, version_freeze_hash VARCHAR(128), checks_json CLOB, issues_json CLOB, suggestions_json CLOB, context_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        CreativeProjectService projects = new CreativeProjectService(jdbc, new ObjectMapper());
        Map<String, Object> project = projects.createProject(7L, "图片灵感", "凤凰摆件", Map.of());
        projectId = ((Number) project.get("id")).longValue();
        versionId = ((Number) project.get("currentVersionId")).longValue();
        preflight = new CreativePreflightService(jdbc, new ObjectMapper(), projects);
    }

    @Test
    void blocksVersionWithoutProductionMaterialsAndPersistsReport() {
        Map<String, Object> report = preflight.run(projectId, versionId, 7L, Map.of());

        assertThat(report.get("status")).isEqualTo("blocked");
        assertThat((java.util.List<?>) report.get("issues")).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_preflight_report WHERE project_id=? AND version_id=?", Integer.class, projectId, versionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_project_event WHERE project_id=? AND event_type='preflight_completed'", Integer.class, projectId)).isEqualTo(1);
    }

    @Test
    void passesApprovedModelSpecificationAndAiReview() {
        jdbc.update("INSERT INTO digital_asset (asset_type,status,format,tags,metadata_json,prompt,created_by,project_id,version_id) VALUES ('model','approved','glb','3D模型','{\"productMaterial\":\"树脂\",\"productSize\":\"100x80x40mm\"}','凤凰摆件产品模型',7,?,?)", projectId, versionId);
        long modelAssetId = jdbc.queryForObject("SELECT MAX(id) FROM digital_asset WHERE asset_type='model'", Long.class);
        jdbc.update("INSERT INTO digital_asset (asset_type,status,format,tags,metadata_json,prompt,created_by,project_id,version_id) VALUES ('prompt','draft','txt','3D建模','{}','AI 3D建模规格书：尺寸、材质和工艺',7,?,?)", projectId, versionId);
        jdbc.update("INSERT INTO design_review (asset_id,overall_score,recommendation) VALUES (?,95,'go')", modelAssetId);

        Map<String, Object> report = preflight.run(projectId, versionId, 7L, Map.of());

        assertThat(report.get("status")).isEqualTo("passed");
        assertThat(report.get("score")).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT status FROM creative_preflight_report WHERE project_id=? AND version_id=?", String.class, projectId, versionId)).isEqualTo("passed");
    }
}
