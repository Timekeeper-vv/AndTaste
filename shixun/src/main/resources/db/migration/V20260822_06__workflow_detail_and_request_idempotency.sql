-- 第六阶段：统一流程详情读取边界，并为生产申请增加客户端幂等键。
-- 仅增加可空字段和索引，不修改历史申请；NULL 幂等键允许旧客户端继续提交。

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='client_request_key'),
    'ALTER TABLE consumer_production_request ADD COLUMN client_request_key VARCHAR(120) NULL AFTER note',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND index_name='uk_cpr_user_client_request_key'),
    'CREATE UNIQUE INDEX uk_cpr_user_client_request_key ON consumer_production_request (user_id, client_request_key)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
