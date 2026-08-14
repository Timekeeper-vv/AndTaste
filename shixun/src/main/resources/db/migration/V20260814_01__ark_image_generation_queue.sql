-- Persist Ark image requests so account-level concurrency limits are handled
-- by the platform instead of leaking 429 errors to end users.

-- MySQL 8.0.46 has no ADD COLUMN IF NOT EXISTS. Keep every operation
-- resumable because production may contain a partially applied migration.
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND column_name = 'request_payload_json'),
    'SELECT 1',
    'ALTER TABLE ai_generation_job ADD COLUMN request_payload_json JSON NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND column_name = 'attempt_count'),
    'SELECT 1',
    'ALTER TABLE ai_generation_job ADD COLUMN attempt_count INT NOT NULL DEFAULT 0'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND column_name = 'started_at'),
    'SELECT 1',
    'ALTER TABLE ai_generation_job ADD COLUMN started_at DATETIME NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND column_name = 'finished_at'),
    'SELECT 1',
    'ALTER TABLE ai_generation_job ADD COLUMN finished_at DATETIME NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND index_name = 'idx_ai_job_provider_queue'),
    'SELECT 1',
    'CREATE INDEX idx_ai_job_provider_queue ON ai_generation_job (provider, job_type, status, id)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND index_name = 'idx_ai_job_owner_queue'),
    'SELECT 1',
    'CREATE INDEX idx_ai_job_owner_queue ON ai_generation_job (created_by, provider, job_type, status)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
