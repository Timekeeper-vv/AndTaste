-- 悬赏投稿收集投稿人实名信息：身份证号（18 位）与手机号（11 位）
ALTER TABLE consumer_bounty_submission
    ADD COLUMN id_card VARCHAR(18) NULL COMMENT '投稿人身份证号' AFTER product_description,
    ADD COLUMN contact_phone VARCHAR(20) NULL COMMENT '投稿人手机号' AFTER id_card;
