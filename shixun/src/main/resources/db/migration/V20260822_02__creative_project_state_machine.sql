-- 第二阶段：统一创作阶段流转，并为审核/打样建立不可变版本快照。
-- 所有字段均可为空/可重入，旧版本记录不会被覆盖。

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_project_version')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_project_version' AND column_name='frozen_at'),
    'ALTER TABLE creative_project_version ADD COLUMN frozen_at DATETIME NULL AFTER status',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_project_version')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_project_version' AND column_name='frozen_by'),
    'ALTER TABLE creative_project_version ADD COLUMN frozen_by BIGINT NULL AFTER frozen_at',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_project_version')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_project_version' AND column_name='freeze_reason'),
    'ALTER TABLE creative_project_version ADD COLUMN freeze_reason VARCHAR(500) NULL AFTER frozen_by',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_project_version')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_project_version' AND column_name='freeze_hash'),
    'ALTER TABLE creative_project_version ADD COLUMN freeze_hash VARCHAR(128) NULL AFTER freeze_reason',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_project_version')
    AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='creative_project_version' AND index_name='idx_cppv_frozen'),
    'CREATE INDEX idx_cppv_frozen ON creative_project_version (project_id, status, frozen_at)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- A production request keeps the exact frozen design snapshot used at
-- submission time. These columns are additive so historical requests remain
-- readable and can be backfilled gradually.
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='version_snapshot_json'),
    'ALTER TABLE consumer_production_request ADD COLUMN version_snapshot_json JSON NULL AFTER version_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='version_snapshot_hash'),
    'ALTER TABLE consumer_production_request ADD COLUMN version_snapshot_hash VARCHAR(128) NULL AFTER version_snapshot_json',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='version_frozen_at'),
    'ALTER TABLE consumer_production_request ADD COLUMN version_frozen_at DATETIME NULL AFTER version_snapshot_hash',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND index_name='idx_cpr_snapshot_hash'),
    'CREATE INDEX idx_cpr_snapshot_hash ON consumer_production_request (version_snapshot_hash)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Existing records remain usable. New writes are normalized by
-- CreativeProjectService to the canonical phases:
-- brief -> generation -> multiview -> preflight -> ai_review ->
-- human_review -> sampling -> completed.
