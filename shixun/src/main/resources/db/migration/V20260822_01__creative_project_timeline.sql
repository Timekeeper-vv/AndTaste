-- 第一阶段：统一创作项目、版本和时间线。
--
-- 这次迁移只增加可空关联和新的审计表，不重写任何历史业务数据。
-- 生产库可能已经通过 and_taste_schema.sql 建过旧版 creative_project，
-- 因此所有补列/补索引均通过 information_schema 判断后执行，保证可重入。

CREATE TABLE IF NOT EXISTS creative_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NULL COMMENT 'C端创建人；历史B端项目可为空',
    tenant_id BIGINT NULL,
    name VARCHAR(180) NOT NULL,
    theme VARCHAR(300) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'planning',
    current_phase VARCHAR(40) NULL COMMENT 'brief/generation/multiview/preflight/ai_review/human_review/sampling/completed',
    current_version_id BIGINT NULL,
    next_action VARCHAR(160) NULL,
    metadata_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cp_user_updated (user_id, updated_at),
    INDEX idx_cp_phase_updated (current_phase, updated_at)
) COMMENT='统一C端/后台创作项目';

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_project')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_project' AND column_name='user_id'),
    'ALTER TABLE creative_project ADD COLUMN user_id BIGINT NULL COMMENT ''C端创建人；历史B端项目可为空'' AFTER project_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_project')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_project' AND column_name='current_phase'),
    'ALTER TABLE creative_project ADD COLUMN current_phase VARCHAR(40) NULL COMMENT ''brief/generation/multiview/preflight/ai_review/human_review/sampling/completed'' AFTER status',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_project')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_project' AND column_name='current_version_id'),
    'ALTER TABLE creative_project ADD COLUMN current_version_id BIGINT NULL AFTER current_phase',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_project')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_project' AND column_name='next_action'),
    'ALTER TABLE creative_project ADD COLUMN next_action VARCHAR(160) NULL AFTER current_version_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_project')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_project' AND column_name='metadata_json'),
    'ALTER TABLE creative_project ADD COLUMN metadata_json JSON NULL AFTER next_action',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='creative_project' AND index_name='idx_cp_user_updated'),
    'SELECT 1',
    'CREATE INDEX idx_cp_user_updated ON creative_project (user_id, updated_at)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='creative_project' AND index_name='idx_cp_phase_updated'),
    'SELECT 1',
    'CREATE INDEX idx_cp_phase_updated ON creative_project (current_phase, updated_at)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS creative_project_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version_no VARCHAR(100) NOT NULL UNIQUE,
    version_number INT NOT NULL,
    version_label VARCHAR(160) NULL,
    phase VARCHAR(40) NOT NULL DEFAULT 'brief',
    status VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT 'draft/active/approved/archived',
    brief_json JSON NULL,
    metadata_json JSON NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cppv_project_number (project_id, version_number),
    INDEX idx_cppv_project_updated (project_id, updated_at),
    INDEX idx_cppv_phase (phase, status)
) COMMENT='创作项目版本快照';

CREATE TABLE IF NOT EXISTS creative_project_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version_id BIGINT NULL,
    user_id BIGINT NULL,
    event_type VARCHAR(60) NOT NULL,
    from_phase VARCHAR(40) NULL,
    to_phase VARCHAR(40) NULL,
    next_action VARCHAR(160) NULL,
    actor_type VARCHAR(30) NOT NULL DEFAULT 'user' COMMENT 'user/system/ai/staff',
    actor_id BIGINT NULL,
    idempotency_key VARCHAR(120) NULL,
    payload_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cp_event_project_time (project_id, created_at, id),
    INDEX idx_cp_event_version_time (version_id, created_at, id),
    INDEX idx_cp_event_user_time (user_id, created_at),
    UNIQUE KEY uk_cp_event_idempotency (project_id, idempotency_key)
) COMMENT='创作项目时间线事件';

-- 将已有流程记录逐步纳入项目；所有关联均可为空，历史数据保持原样。
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_conversation_session')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_conversation_session' AND column_name='project_id'),
    'ALTER TABLE creative_conversation_session ADD COLUMN project_id BIGINT NULL AFTER user_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_conversation_session')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_conversation_session' AND column_name='version_id'),
    'ALTER TABLE creative_conversation_session ADD COLUMN version_id BIGINT NULL AFTER project_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='creative_conversation_session' AND index_name='idx_ccs_project_updated'),
    'SELECT 1',
    'CREATE INDEX idx_ccs_project_updated ON creative_conversation_session (project_id, updated_at)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_conversation_event')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_conversation_event' AND column_name='project_id'),
    'ALTER TABLE creative_conversation_event ADD COLUMN project_id BIGINT NULL AFTER session_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_conversation_event')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_conversation_event' AND column_name='version_id'),
    'ALTER TABLE creative_conversation_event ADD COLUMN version_id BIGINT NULL AFTER project_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='creative_conversation_event' AND index_name='idx_cce_project_time'),
    'SELECT 1',
    'CREATE INDEX idx_cce_project_time ON creative_conversation_event (project_id, created_at, id)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Repeated blocks are deliberately metadata guarded for databases restored from
-- an older release where one or more optional workflow tables may be absent.
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='ai_generation_job') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_generation_job' AND column_name='project_id'), 'ALTER TABLE ai_generation_job ADD COLUMN project_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='ai_generation_job') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_generation_job' AND column_name='version_id'), 'ALTER TABLE ai_generation_job ADD COLUMN version_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='ai_generation_job') AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='ai_generation_job' AND index_name='idx_ai_job_project_status'), 'SELECT 1', IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='ai_generation_job'), 'CREATE INDEX idx_ai_job_project_status ON ai_generation_job (project_id, status, id)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='digital_asset') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='digital_asset' AND column_name='project_id'), 'ALTER TABLE digital_asset ADD COLUMN project_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='digital_asset') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='digital_asset' AND column_name='version_id'), 'ALTER TABLE digital_asset ADD COLUMN version_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='digital_asset') AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='digital_asset' AND index_name='idx_asset_project_version'), 'SELECT 1', IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='digital_asset'), 'CREATE INDEX idx_asset_project_version ON digital_asset (project_id, version_id, id)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='design_review') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='design_review' AND column_name='project_id'), 'ALTER TABLE design_review ADD COLUMN project_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='design_review') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='design_review' AND column_name='version_id'), 'ALTER TABLE design_review ADD COLUMN version_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='design_review') AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='design_review' AND index_name='idx_review_project_version'), 'SELECT 1', IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='design_review'), 'CREATE INDEX idx_review_project_version ON design_review (project_id, version_id, id)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle' AND column_name='project_id'), 'ALTER TABLE creative_multiview_bundle ADD COLUMN project_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle' AND column_name='version_id'), 'ALTER TABLE creative_multiview_bundle ADD COLUMN version_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle') AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle' AND index_name='idx_cmb_project_version'), 'SELECT 1', IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle'), 'CREATE INDEX idx_cmb_project_version ON creative_multiview_bundle (project_id, version_id, id)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='project_id'), 'ALTER TABLE consumer_production_request ADD COLUMN project_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='version_id'), 'ALTER TABLE consumer_production_request ADD COLUMN version_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request') AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND index_name='idx_cpr_project_version'), 'SELECT 1', IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request'), 'CREATE INDEX idx_cpr_project_version ON consumer_production_request (project_id, version_id, id)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
