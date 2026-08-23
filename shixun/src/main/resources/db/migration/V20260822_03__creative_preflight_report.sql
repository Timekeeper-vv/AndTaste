-- 第三阶段：把生产可行性预检保存为项目版本级报告。
-- 每次预检都保留一份快照，便于审核、打样和返修时追溯当时使用的资料。

CREATE TABLE IF NOT EXISTS creative_preflight_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL COMMENT 'passed/needs_review/blocked',
    score INT NOT NULL DEFAULT 0,
    version_freeze_hash VARCHAR(128) NULL,
    checks_json JSON NULL,
    issues_json JSON NULL,
    suggestions_json JSON NULL,
    context_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cpf_project_version (project_id, version_id, id),
    INDEX idx_cpf_user_created (user_id, created_at)
) COMMENT='项目版本生产可行性预检报告';
