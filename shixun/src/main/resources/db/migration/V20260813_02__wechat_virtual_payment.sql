-- 微信小程序虚拟支付扩展。积分充值使用官方代币充值能力，实物打样费仍使用。
-- 普通微信支付；session_key 只保存 AES-GCM 密文，绝不返回给客户端。

-- MySQL 8.0.46 does not support ADD COLUMN IF NOT EXISTS. Check metadata so
-- the migration can safely resume if an earlier attempt added either column.
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wechat_user_binding' AND column_name = 'session_key_ciphertext'),
    'SELECT 1',
    'ALTER TABLE wechat_user_binding ADD COLUMN session_key_ciphertext TEXT NULL COMMENT ''微信临时 session_key 的 AES-GCM 密文'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wechat_user_binding' AND column_name = 'session_key_updated_at'),
    'SELECT 1',
    'ALTER TABLE wechat_user_binding ADD COLUMN session_key_updated_at DATETIME NULL COMMENT ''session_key 最近刷新时间'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS payment_virtual_order (
    order_no VARCHAR(64) NOT NULL PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    offer_id VARCHAR(128) NOT NULL,
    env TINYINT NOT NULL,
    payer_openid VARCHAR(128) NOT NULL,
    expected_coin_quantity BIGINT NOT NULL,
    balance_before BIGINT NOT NULL,
    balance_after BIGINT NULL,
    provider_transaction_id VARCHAR(128) NULL,
    provider_order_no VARCHAR(128) NULL,
    provider_status VARCHAR(32) NOT NULL DEFAULT 'PREPARED',
    last_reconciled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_virtual_provider_transaction (provider_transaction_id),
    INDEX idx_virtual_openid_status (payer_openid, provider_status)
) COMMENT='微信小程序虚拟支付代币充值核验元数据';
