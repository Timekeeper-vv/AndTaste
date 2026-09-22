-- Create project_asset table to store multiple assets (images, models, documents)
-- associated with a project for designer/PM review
CREATE TABLE IF NOT EXISTS project_asset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL COMMENT '项目ID',
    asset_id BIGINT NOT NULL COMMENT '关联的digital_asset.id',
    asset_type VARCHAR(50) NOT NULL COMMENT '资产类型：front/side/top/model/sketch/rendering等',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_url VARCHAR(500) NOT NULL COMMENT '文件URL',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    mime_type VARCHAR(100) COMMENT 'MIME类型',
    created_by VARCHAR(100) NOT NULL COMMENT '创建人username',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_project_id (project_id),
    INDEX idx_asset_id (asset_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目资产表';
