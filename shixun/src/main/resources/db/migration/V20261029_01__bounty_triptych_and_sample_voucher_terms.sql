ALTER TABLE consumer_bounty_submission
    ADD COLUMN bundle_id BIGINT NULL AFTER asset_id,
    ADD COLUMN view_asset_ids_json JSON NULL AFTER bundle_id;

ALTER TABLE consumer_bounty_sample_voucher
    ADD COLUMN bundle_id BIGINT NULL AFTER user_id,
    ADD COLUMN material VARCHAR(40) NOT NULL DEFAULT '树脂' AFTER bundle_id,
    ADD COLUMN sample_quantity INT NOT NULL DEFAULT 1 AFTER material,
    ADD COLUMN shipping_included TINYINT(1) NOT NULL DEFAULT 1 AFTER sample_quantity,
    ADD COLUMN expires_at DATETIME NULL AFTER shipping_included,
    ADD COLUMN production_request_id BIGINT NULL AFTER expires_at;

UPDATE consumer_bounty_sample_voucher
SET expires_at=DATE_ADD(created_at,INTERVAL 60 DAY)
WHERE expires_at IS NULL;

ALTER TABLE consumer_production_request
    ADD COLUMN sample_voucher_id BIGINT NULL AFTER sample_payment_status;

UPDATE consumer_bounty_task
SET brief='围绕云南省博物馆馆藏文物进行原创文创设计，品类不限。提交作品需填写产品名称、品类、特色功能、文物原型和产品介绍，并提交全色正面、侧面、背面三视图；每张仅支持 JPG，文件大小 1–3 MB。投稿作品一经遴选采用，平台联系原创作者洽谈合作。每个账号最多提交5件作品。'
WHERE campaign_key='yunnan_museum_cultural_product_2026' AND status='draft';
