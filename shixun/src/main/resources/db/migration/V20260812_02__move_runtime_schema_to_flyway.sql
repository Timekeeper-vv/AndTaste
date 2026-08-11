-- All tables previously created from request handlers or bean constructors are
-- owned here from now on. This migration is intentionally idempotent so it is
-- safe for the first Flyway deployment of an existing production database.

CREATE TABLE IF NOT EXISTS payment_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    amount_fen BIGINT NOT NULL,
    credit_amount DECIMAL(12,2) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    provider_order_no VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    code_url TEXT NULL,
    provider_response TEXT NULL,
    paid_at DATETIME NULL,
    expired_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_payment_user (user_id),
    INDEX idx_payment_status (status),
    UNIQUE KEY uk_provider_order (channel, provider_order_no)
) COMMENT='C端额度支付订单';

CREATE TABLE IF NOT EXISTS payment_callback_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(128) NULL,
    payload_json LONGTEXT NOT NULL,
    verified TINYINT NOT NULL DEFAULT 0,
    processed TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_event (channel, provider_event_id)
) COMMENT='支付回调审计日志';

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

CREATE TABLE IF NOT EXISTS design_review_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL UNIQUE,
    report_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='智能评估完整报告留存';

CREATE TABLE IF NOT EXISTS consumer_credit_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    frozen_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_recharged DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_consumed DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='C端用户额度账户';

CREATE TABLE IF NOT EXISTS consumer_credit_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NULL,
    job_id BIGINT NULL,
    biz_type VARCHAR(50) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    balance_before DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    balance_after DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    remark VARCHAR(500),
    operator VARCHAR(80),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_credit_user (user_id),
    INDEX idx_credit_status (status),
    INDEX idx_credit_biz (biz_type)
) COMMENT='C端用户额度流水';

CREATE TABLE IF NOT EXISTS consumer_reward_mission_claim (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    mission_key VARCHAR(80) NOT NULL,
    asset_id BIGINT NULL,
    credit_transaction_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'claimed',
    claimed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_consumer_reward_mission (user_id, mission_key),
    INDEX idx_reward_mission_user (user_id)
) COMMENT='C端一次性创作任务奖励领取记录';

CREATE TABLE IF NOT EXISTS consumer_campaign_reward (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    participation_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    campaign_key VARCHAR(80) NOT NULL,
    asset_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'pending_review',
    reward_amount DECIMAL(12,2) NOT NULL,
    credit_transaction_id BIGINT NULL,
    reviewed_by VARCHAR(80),
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_consumer_campaign_user (user_id, campaign_key),
    UNIQUE KEY uq_consumer_campaign_asset (asset_id),
    INDEX idx_campaign_reward_status (status),
    INDEX idx_campaign_reward_user (user_id)
) COMMENT='C端主题活动投稿与奖励记录';

CREATE TABLE IF NOT EXISTS consumer_production_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    title VARCHAR(200),
    quantity INT NOT NULL DEFAULT 1,
    self_ship_quantity INT NOT NULL DEFAULT 0,
    museum_distribution_json TEXT,
    recipient_name VARCHAR(80),
    recipient_phone VARCHAR(80),
    recipient_address VARCHAR(500),
    note VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'review',
    review_comment VARCHAR(1000),
    reviewed_by VARCHAR(80),
    reviewed_at DATETIME NULL,
    sample_product_name VARCHAR(120),
    sample_fee_yuan DECIMAL(10,2) NULL,
    sample_payment_status VARCHAR(24) NOT NULL DEFAULT 'not_required',
    sample_payment_order_no VARCHAR(64) NULL,
    sample_paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cpr_user (user_id),
    INDEX idx_cpr_asset (asset_id),
    INDEX idx_cpr_type (request_type),
    INDEX idx_cpr_status (status),
    INDEX idx_cpr_sample_payment (sample_payment_status)
) COMMENT='C端作品打样与生产申请';

CREATE TABLE IF NOT EXISTS consumer_sample_fee_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(120) NOT NULL UNIQUE,
    fee_yuan DECIMAL(10,2) NOT NULL,
    source_file VARCHAR(255) NOT NULL DEFAULT '工作簿2.xlsx',
    source_sheet VARCHAR(120) NOT NULL DEFAULT 'Sheet1',
    active TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sample_fee_active (active)
) COMMENT='打样费用目录（服务端定价）';

CREATE TABLE IF NOT EXISTS creative_rights_consultation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NULL,
    service_type VARCHAR(80) NOT NULL,
    note VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rights_consultation_user (user_id),
    INDEX idx_rights_consultation_status (status)
) COMMENT='C端版权服务咨询记录';

INSERT INTO consumer_sample_fee_catalog (product_name, fee_yuan) VALUES
    ('合金冰箱贴', 2000), ('胸针/徽章', 1300), ('慕斯蛋糕', 2500),
    ('亚克力冰箱贴', 1000), ('针织包', 1000), ('马卡龙', 2500),
    ('树脂冰箱贴', 2500), ('帆布包', 500), ('曲奇饼干', 2500),
    ('陶瓷冰箱贴', 2000), ('摇摇笔', 1200), ('毛绒', 2000),
    ('橡皮', 1000), ('搪胶脸毛绒', 5000), ('服饰', 800),
    ('毛绒挂件', 2000), ('保温杯', 1000), ('金属挂件', 2000),
    ('笔记本', 1000), ('树脂摆件', 3000), ('磁吸笔记本', 2500),
    ('亚克力摆件', 1000), ('冰淇淋', 2000), ('叶雕灯', 1000),
    ('棒棒糖', 2000), ('考古挖掘盲盒', 3500), ('巧克力', 2000)
ON DUPLICATE KEY UPDATE
    fee_yuan = VALUES(fee_yuan),
    active = 1,
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS consumer_professional_submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    original_name VARCHAR(260) NOT NULL,
    storage_name VARCHAR(260) NOT NULL,
    file_size BIGINT NOT NULL,
    purpose VARCHAR(30) NOT NULL DEFAULT 'personal',
    museum_id VARCHAR(80),
    museum_name VARCHAR(200),
    note VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'review',
    review_comment VARCHAR(1000),
    reviewed_by VARCHAR(80),
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cps_user (user_id),
    INDEX idx_cps_status (status)
) COMMENT='C端专业设计师ZIP作品包审核';

CREATE TABLE IF NOT EXISTS customer_service_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    human_takeover TINYINT NOT NULL DEFAULT 0,
    taken_by BIGINT NULL,
    taken_by_name VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer_service_user (user_id),
    INDEX idx_customer_service_updated (updated_at)
) COMMENT='C端客服会话';

CREATE TABLE IF NOT EXISTS customer_service_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    sender_id BIGINT NULL,
    sender_name VARCHAR(100),
    content TEXT NOT NULL,
    read_by_user TINYINT NOT NULL DEFAULT 0,
    read_by_staff TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_customer_service_message_conversation (conversation_id)
) COMMENT='客服消息';

ALTER TABLE user_compliance_consent
    ADD COLUMN IF NOT EXISTS signature_name VARCHAR(100) NULL;
ALTER TABLE digital_asset
    ADD COLUMN IF NOT EXISTS created_by BIGINT NULL;
ALTER TABLE ai_generation_job
    ADD COLUMN IF NOT EXISTS created_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS credit_transaction_id BIGINT NULL;
ALTER TABLE workflow_application
    ADD COLUMN IF NOT EXISTS flow_type VARCHAR(50) NOT NULL DEFAULT 'standard',
    ADD COLUMN IF NOT EXISTS flow_name VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS flow_config_json JSON NULL,
    ADD COLUMN IF NOT EXISTS current_step INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS current_step_name VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS current_handler VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS current_approval_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS resubmit_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS withdrawn_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS finished_at DATETIME NULL;
ALTER TABLE workflow_approval_log
    ADD COLUMN IF NOT EXISTS step_index INT NULL,
    ADD COLUMN IF NOT EXISTS step_name VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS approval_round INT NOT NULL DEFAULT 0;
-- MySQL 8 does not support CREATE INDEX IF NOT EXISTS. Check metadata so a
-- partially applied production migration can be repaired and safely rerun.
SET @workflow_log_step_index_exists := (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'workflow_approval_log'
       AND index_name = 'idx_workflow_log_step'
);
SET @workflow_log_step_index_sql := IF(
    @workflow_log_step_index_exists = 0,
    'CREATE INDEX idx_workflow_log_step ON workflow_approval_log(application_id, action, step_index, approval_round)',
    'SELECT 1'
);
PREPARE workflow_log_step_index_statement FROM @workflow_log_step_index_sql;
EXECUTE workflow_log_step_index_statement;
DEALLOCATE PREPARE workflow_log_step_index_statement;
CREATE TABLE IF NOT EXISTS workflow_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    receiver VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    read_flag TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_workflow_notice_receiver (receiver, read_flag),
    INDEX idx_workflow_notice_app (application_id)
) COMMENT='审批通知';

UPDATE workflow_application
   SET flow_type=COALESCE(flow_type, 'standard'),
       flow_name='四人会签审批',
       current_step=0,
       current_step_name='四人会签审批',
       current_handler='审批员1/审批员2/审批员3/审批员4'
 WHERE deleted=0 AND status='pending';
