-- 修订与专业指导是商品化申请的补充流程：
-- 用户可上传本地修改图重提，或购买专业指导后再重提原申请。

CREATE TABLE IF NOT EXISTS commercial_application_revision (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_type VARCHAR(30) NOT NULL COMMENT 'quote/consignment',
    application_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    previous_asset_id BIGINT NULL,
    asset_id BIGINT NOT NULL,
    note VARCHAR(1200) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_car_application (application_type, application_id, created_at),
    INDEX idx_car_user (user_id, created_at)
) COMMENT='商品化申请的本地修改图重提记录';

CREATE TABLE IF NOT EXISTS commercial_professional_guidance_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    guidance_no VARCHAR(80) NOT NULL UNIQUE,
    application_type VARCHAR(30) NOT NULL COMMENT 'quote/consignment',
    application_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NULL,
    product_template_id BIGINT NULL,
    request_note VARCHAR(1200) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'requested' COMMENT 'requested/quoted/in_progress/completed/closed',
    quoted_fee_yuan DECIMAL(12,2) NULL,
    quoted_lead_time VARCHAR(120) NULL,
    operator_comment VARCHAR(1200) NULL,
    guidance_result VARCHAR(3000) NULL,
    payment_status VARCHAR(24) NOT NULL DEFAULT 'not_required' COMMENT 'not_required/unpaid/pending/manual_review/paid',
    payment_order_no VARCHAR(64) NULL,
    paid_at DATETIME NULL,
    quoted_by VARCHAR(80) NULL,
    quoted_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cpgr_user (user_id, created_at),
    INDEX idx_cpgr_status (status, created_at),
    INDEX idx_cpgr_application (application_type, application_id),
    INDEX idx_cpgr_payment (payment_status, payment_order_no)
) COMMENT='商品化申请专业指导工单与收费状态';
