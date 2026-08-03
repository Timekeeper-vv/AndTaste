-- 微信小程序 JSAPI 支付、退款与主动对账扩展。
-- 先在维护窗口备份数据库，再执行本脚本；不修改既有 payment_order 的字段，
-- 以独立扩展表避免线上金融订单 ALTER 带来的锁表风险。

CREATE TABLE IF NOT EXISTS wechat_user_binding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    app_id VARCHAR(64) NOT NULL,
    openid VARCHAR(128) NOT NULL,
    bound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wechat_user_app (user_id, app_id),
    UNIQUE KEY uk_wechat_app_openid (app_id, openid),
    INDEX idx_wechat_binding_user (user_id)
) COMMENT='小程序微信OpenID绑定（不得暴露给客户端）';

CREATE TABLE IF NOT EXISTS payment_wechat_order (
    order_no VARCHAR(64) NOT NULL PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    mch_id VARCHAR(64) NOT NULL,
    payer_openid VARCHAR(128) NULL,
    prepay_id VARCHAR(128) NULL,
    transaction_id VARCHAR(128) NULL,
    provider_trade_state VARCHAR(32) NULL,
    last_reconciled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wechat_transaction (transaction_id)
) COMMENT='微信支付订单安全校验元数据';

CREATE TABLE IF NOT EXISTS payment_refund (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_no VARCHAR(64) NOT NULL UNIQUE,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    amount_fen BIGINT NOT NULL,
    credit_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(240) NULL,
    provider_refund_id VARCHAR(128) NULL,
    provider_response TEXT NULL,
    requested_by BIGINT NOT NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_refund (provider_refund_id),
    INDEX idx_refund_status (status)
) COMMENT='微信充值退款及额度冻结流水';

CREATE TABLE IF NOT EXISTS payment_daily_reconciliation (
    bill_date DATE NOT NULL,
    bill_type VARCHAR(24) NOT NULL,
    status VARCHAR(32) NOT NULL,
    download_sha256 CHAR(64) NULL,
    download_bytes BIGINT NULL,
    local_record_count INT NOT NULL DEFAULT 0,
    provider_record_count INT NULL,
    matched_record_count INT NULL,
    discrepancy_count INT NULL,
    result_summary TEXT NULL,
    verified_by BIGINT NULL,
    verified_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (bill_date, bill_type)
) COMMENT='微信支付日账单下载与本地比对审计';
