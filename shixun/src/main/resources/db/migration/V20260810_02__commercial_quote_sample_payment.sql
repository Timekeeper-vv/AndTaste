-- 商品化报价申请的打样费支付状态。
-- 金额始终取 creative_quote_request.quoted_total_price，客户端不能提交金额。

ALTER TABLE creative_quote_request ADD COLUMN sample_payment_status VARCHAR(24) NOT NULL DEFAULT 'not_required';
ALTER TABLE creative_quote_request ADD COLUMN sample_payment_order_no VARCHAR(64) NULL;
ALTER TABLE creative_quote_request ADD COLUMN sample_paid_at DATETIME NULL;
ALTER TABLE creative_quote_request ADD INDEX idx_cqr_sample_payment (sample_payment_status, sample_payment_order_no);
