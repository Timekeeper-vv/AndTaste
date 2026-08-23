-- 第四阶段：样品反馈、返修、验收及量产解锁。
-- 所有字段可空/有默认值，兼容已经存在的生产申请和滚动发布。

CREATE TABLE IF NOT EXISTS creative_sample_lifecycle_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    version_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL COMMENT 'received/feedback/revision_requested/accepted/rejected/bulk_unlocked',
    decision VARCHAR(32) NULL COMMENT 'accept/revision_required/reject',
    rating INT NULL,
    comment VARCHAR(2000) NULL,
    issue_tags_json JSON NULL,
    evidence_asset_ids_json JSON NULL,
    payload_json JSON NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_csle_request_time (request_id, created_at, id),
    INDEX idx_csle_project_version (project_id, version_id, created_at),
    INDEX idx_csle_type (event_type)
) COMMENT='样品生命周期反馈与验收记录';

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='sample_workflow_status'),
    'ALTER TABLE consumer_production_request ADD COLUMN sample_workflow_status VARCHAR(32) NOT NULL DEFAULT ''not_started'' AFTER status',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='sample_received_at'),
    'ALTER TABLE consumer_production_request ADD COLUMN sample_received_at DATETIME NULL AFTER sample_workflow_status',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='sample_accepted_at'),
    'ALTER TABLE consumer_production_request ADD COLUMN sample_accepted_at DATETIME NULL AFTER sample_received_at',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='sample_revision_count'),
    'ALTER TABLE consumer_production_request ADD COLUMN sample_revision_count INT NOT NULL DEFAULT 0 AFTER sample_accepted_at',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='bulk_unlocked_at'),
    'ALTER TABLE consumer_production_request ADD COLUMN bulk_unlocked_at DATETIME NULL AFTER sample_revision_count',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='bulk_unlocked_by'),
    'ALTER TABLE consumer_production_request ADD COLUMN bulk_unlocked_by BIGINT NULL AFTER bulk_unlocked_at',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND index_name='idx_cpr_sample_workflow'),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request'), 'CREATE INDEX idx_cpr_sample_workflow ON consumer_production_request (user_id, request_type, sample_workflow_status, updated_at)', 'SELECT 1')
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
