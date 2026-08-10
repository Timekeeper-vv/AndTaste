-- 对话式创作：保存用户每一步选择、输入和生成结果。
-- payload_json 只保存创作参数和作品编号，不保存 SessionKey、支付密钥等敏感信息。

CREATE TABLE IF NOT EXISTS creative_conversation_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    mode VARCHAR(24) NULL COMMENT 'template/text/image',
    product_type VARCHAR(120) NULL,
    material VARCHAR(120) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'draft' COMMENT 'draft/generating/completed/archived',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ccs_user_time (user_id, updated_at),
    INDEX idx_ccs_status (status, updated_at)
) COMMENT='C端对话式创作会话';

CREATE TABLE IF NOT EXISTS creative_conversation_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    step VARCHAR(40) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cce_session_time (session_id, created_at),
    INDEX idx_cce_user_time (user_id, created_at)
) COMMENT='C端对话式创作步骤事件';
