-- 三视图作为一个完整作品包进入人工审核。
-- 子图仍保留在 digital_asset 中，作品包负责承载整体审核状态和后续打样关联。

CREATE TABLE IF NOT EXISTS creative_multiview_bundle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bundle_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    input_asset_id BIGINT NULL,
    product_key VARCHAR(120) NULL,
    product_name VARCHAR(180) NULL,
    material VARCHAR(180) NULL,
    product_size VARCHAR(120) NULL,
    view_count INT NOT NULL DEFAULT 3,
    status VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT 'draft/review/approved/rejected/archived',
    purpose VARCHAR(30) NULL COMMENT 'personal/museum_sale',
    museum_id VARCHAR(80) NULL,
    museum_name VARCHAR(200) NULL,
    campaign_key VARCHAR(100) NULL,
    note VARCHAR(1200) NULL,
    review_comment VARCHAR(1200) NULL,
    reviewed_by VARCHAR(80) NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cmb_user_input (user_id, input_asset_id),
    INDEX idx_cmb_user_status (user_id, status, updated_at),
    INDEX idx_cmb_review_status (status, updated_at)
) COMMENT='C端三视图完整作品包';

CREATE TABLE IF NOT EXISTS creative_multiview_bundle_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bundle_id BIGINT NOT NULL,
    view_key VARCHAR(20) NOT NULL COMMENT 'front/left/back/right',
    asset_id BIGINT NOT NULL,
    label VARCHAR(40) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cmvi_bundle_view (bundle_id, view_key),
    UNIQUE KEY uk_cmvi_asset (asset_id),
    INDEX idx_cmvi_bundle (bundle_id)
) COMMENT='三视图作品包视角明细';

-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用元数据判断保证重复部署可恢复。
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'consumer_production_request' AND column_name = 'multiview_bundle_id'),
    'SELECT 1',
    'ALTER TABLE consumer_production_request ADD COLUMN multiview_bundle_id BIGINT NULL AFTER asset_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'consumer_production_request' AND index_name = 'idx_cpr_multiview_bundle'),
    'SELECT 1',
    'ALTER TABLE consumer_production_request ADD INDEX idx_cpr_multiview_bundle (multiview_bundle_id)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
