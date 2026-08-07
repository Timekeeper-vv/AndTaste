-- One-time email verification codes. Store only a digest, never the code.
CREATE TABLE IF NOT EXISTS user_email_verification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    used_at DATETIME NULL,
    requested_ip_hash CHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email_verification_lookup (email, purpose, used_at, expires_at),
    INDEX idx_email_verification_created (created_at)
) COMMENT='邮箱验证码，仅保存不可逆摘要';
