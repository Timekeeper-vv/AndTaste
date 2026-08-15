-- Image-edit and multi-view jobs can finish after the client leaves the page.
-- Store their structured results so a later poll can rebuild signed asset URLs.

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND column_name = 'result_payload_json'),
    'SELECT 1',
    'ALTER TABLE ai_generation_job ADD COLUMN result_payload_json JSON NULL AFTER request_payload_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
