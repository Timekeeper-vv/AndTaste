-- Link a project to the representative digital_asset (image/3D model) the
-- customer generated, so approvers in the project workbench can preview and
-- download the original file instead of only seeing text fields.
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='project')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='project' AND column_name='asset_id'),
    'ALTER TABLE project ADD COLUMN asset_id BIGINT NULL COMMENT ''关联的作品(digital_asset.id)，用于审批时预览/下载'' AFTER product_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
