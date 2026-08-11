-- First migration after the pre-Flyway baseline. Keeping a small, meaningful
-- metadata table proves that future schema changes are applied by Flyway and
-- gives operations a place to record the deployed application revision.
CREATE TABLE IF NOT EXISTS application_runtime_metadata (
    metadata_key VARCHAR(100) NOT NULL PRIMARY KEY,
    metadata_value VARCHAR(500) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='应用运行版本与运维元数据';

INSERT INTO application_runtime_metadata (metadata_key, metadata_value)
VALUES ('schema_baseline', '20260811.01')
ON DUPLICATE KEY UPDATE metadata_value=VALUES(metadata_value), updated_at=CURRENT_TIMESTAMP;
