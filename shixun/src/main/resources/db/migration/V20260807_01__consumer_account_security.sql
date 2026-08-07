-- Commercial account lifecycle fields and privacy-safe login audit.
-- The deployment script runs this with mysql --force for existing databases.
ALTER TABLE user ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE user ADD COLUMN last_login_at DATETIME NULL;
ALTER TABLE user ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active';
UPDATE user SET status='active' WHERE status IS NULL OR status='';
CREATE INDEX idx_user_status ON user(status);

CREATE TABLE IF NOT EXISTS user_login_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    provider VARCHAR(30) NOT NULL,
    result VARCHAR(20) NOT NULL,
    failure_code VARCHAR(80) NULL,
    ip_hash CHAR(64) NULL,
    user_agent_hash CHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_login_audit_user_time (user_id, created_at),
    INDEX idx_login_audit_created (created_at),
    INDEX idx_login_audit_result (result, created_at)
) COMMENT='账号登录安全审计，不保存原始IP或User-Agent';
