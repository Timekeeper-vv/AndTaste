-- 专业设计师 ZIP 作品包审批通过后的打样报价与支付状态。
-- 报价金额只由后台写入，支付订单金额始终从本表读取，客户端不能提交金额。

ALTER TABLE consumer_professional_submission
    ADD COLUMN quoted_sample_fee_yuan DECIMAL(12,2) NULL AFTER review_comment,
    ADD COLUMN quoted_sample_lead_time VARCHAR(120) NULL AFTER quoted_sample_fee_yuan,
    ADD COLUMN quoted_sample_note VARCHAR(1200) NULL AFTER quoted_sample_lead_time,
    ADD COLUMN sample_payment_status VARCHAR(24) NOT NULL DEFAULT 'not_required' AFTER quoted_sample_note,
    ADD COLUMN sample_payment_order_no VARCHAR(64) NULL AFTER sample_payment_status,
    ADD COLUMN sample_paid_at DATETIME NULL AFTER sample_payment_order_no,
    ADD INDEX idx_cps_sample_payment (sample_payment_status, sample_payment_order_no);
