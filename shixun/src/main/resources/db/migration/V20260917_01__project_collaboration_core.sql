-- Project-centric collaboration model.
--
-- Every cultural/creative product becomes a `project`. Its lifecycle is a
-- fixed sequence of `project_task` rows (one active task per stage), worked
-- on by `project_member`s, with every action recorded in `project_log`.
-- This sits alongside the existing workflow_application system (kept for
-- HR/finance/attendance/etc. approvals); production-type submissions get a
-- linked project so people can work from a single project view instead of
-- hunting across menus.

CREATE TABLE IF NOT EXISTS project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_no VARCHAR(50) NOT NULL UNIQUE COMMENT '项目编号',
    title VARCHAR(200) NOT NULL COMMENT '项目名称（文创产品名）',
    category VARCHAR(30) NOT NULL DEFAULT 'sample' COMMENT '项目类型：sample/bulk',
    product_no VARCHAR(40) NULL COMMENT '关联的产品编号(creative_product.product_no)',
    workflow_application_id BIGINT NULL COMMENT '关联的审批单(兼容旧系统仪表盘)',
    owner_username VARCHAR(100) NOT NULL COMMENT '发起人/客户账号',
    current_stage_index INT NOT NULL DEFAULT 0 COMMENT '当前阶段序号，从0开始',
    current_stage_key VARCHAR(50) NOT NULL DEFAULT 'designer_review' COMMENT '当前阶段编码',
    current_stage_name VARCHAR(100) NOT NULL DEFAULT '设计师初审' COMMENT '当前阶段名称',
    current_assignee_role VARCHAR(30) NOT NULL DEFAULT 'designer' COMMENT '当前阶段负责角色',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/completed/cancelled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    INDEX idx_project_owner (owner_username),
    INDEX idx_project_status (status, updated_at),
    INDEX idx_project_workflow (workflow_application_id),
    INDEX idx_project_stage (current_assignee_role, status)
) COMMENT='项目主表：一个文创产品从设计到发货的全生命周期';

CREATE TABLE IF NOT EXISTS project_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    stage_index INT NOT NULL COMMENT '阶段序号，从0开始',
    stage_key VARCHAR(50) NOT NULL COMMENT '阶段编码',
    stage_name VARCHAR(100) NOT NULL COMMENT '阶段名称',
    assignee_role VARCHAR(30) NOT NULL COMMENT '负责角色',
    assignee_username VARCHAR(100) NULL COMMENT '认领后的具体负责人',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/in_progress/done/rejected/skipped',
    comment VARCHAR(1000) NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_project (project_id, stage_index),
    INDEX idx_task_assignee_role (assignee_role, status),
    INDEX idx_task_assignee_user (assignee_username, status)
) COMMENT='项目任务：项目每个阶段对应一条任务，指派给具体角色处理';

CREATE TABLE IF NOT EXISTS project_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL COMMENT '在此项目中的角色',
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_member (project_id, username),
    INDEX idx_member_user (username)
) COMMENT='项目成员：谁参与了这个项目';

CREATE TABLE IF NOT EXISTS project_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    action VARCHAR(30) NOT NULL COMMENT 'create/claim/complete/reject/comment/transfer',
    operator VARCHAR(100) NOT NULL,
    operator_role VARCHAR(30) NOT NULL,
    content VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_project (project_id, created_at)
) COMMENT='项目日志：全生命周期操作记录（创建/认领/完成/驳回/转交）';

-- Let key asset/request tables point back at a project so files never get
-- separated from the project they belong to. Nullable + additive so existing
-- rows are unaffected; backfill happens as new work is created going forward.
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='digital_asset')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='digital_asset' AND column_name='project_id'),
    'ALTER TABLE digital_asset ADD COLUMN project_id BIGINT NULL AFTER product_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='project_id'),
    'ALTER TABLE consumer_production_request ADD COLUMN project_id BIGINT NULL AFTER product_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_professional_submission')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_professional_submission' AND column_name='project_id'),
    'ALTER TABLE consumer_professional_submission ADD COLUMN project_id BIGINT NULL AFTER product_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='supply_chain_sample_work_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='supply_chain_sample_work_order' AND column_name='project_id'),
    'ALTER TABLE supply_chain_sample_work_order ADD COLUMN project_id BIGINT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='supply_chain_bulk_production_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='supply_chain_bulk_production_order' AND column_name='project_id'),
    'ALTER TABLE supply_chain_bulk_production_order ADD COLUMN project_id BIGINT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
