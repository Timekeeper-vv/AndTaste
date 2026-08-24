-- 一张横向生产模拟图作为三视图作品包的主展示资产。
-- 视角切片仍保存在 creative_multiview_bundle_item，供审核和 3D 建模使用。
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'creative_multiview_bundle')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_multiview_bundle' AND column_name = 'simulation_asset_id'),
    'ALTER TABLE creative_multiview_bundle ADD COLUMN simulation_asset_id BIGINT NULL AFTER input_asset_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'creative_multiview_bundle' AND index_name = 'idx_cmb_simulation_asset'),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'creative_multiview_bundle'),
       'CREATE INDEX idx_cmb_simulation_asset ON creative_multiview_bundle (simulation_asset_id)',
       'SELECT 1')
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
