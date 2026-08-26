-- Complete the product identity backfill introduced after V20260826_02 was
-- deployed. Keep this as a new migration; applied Flyway migrations are
-- immutable and must never be edited in place.
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_quote_request' AND column_name='product_no'),'SELECT 1','ALTER TABLE creative_quote_request ADD COLUMN product_no VARCHAR(40) NULL AFTER request_no'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_consignment_application' AND column_name='product_no'),'SELECT 1','ALTER TABLE creative_consignment_application ADD COLUMN product_no VARCHAR(40) NULL AFTER application_no'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE creative_quote_request q JOIN digital_asset a ON a.id=q.asset_id SET q.product_no=a.product_no WHERE (q.product_no IS NULL OR q.product_no='') AND q.asset_id IS NOT NULL;
UPDATE creative_consignment_application c JOIN digital_asset a ON a.id=c.asset_id SET c.product_no=a.product_no WHERE (c.product_no IS NULL OR c.product_no='') AND c.asset_id IS NOT NULL;

-- Preserve the newest historical request and mark older repeated taps as
-- duplicates instead of deleting audit history. New submissions are blocked
-- in the service while the account row is locked.
UPDATE consumer_production_request old_req
JOIN consumer_production_request new_req
  ON new_req.user_id=old_req.user_id
 AND new_req.product_no=old_req.product_no
 AND new_req.request_type=old_req.request_type
 AND new_req.id>old_req.id
 AND new_req.status IN ('review','approved','processing')
SET old_req.status='duplicate', old_req.review_comment='系统清理：同一产品重复提交'
WHERE old_req.product_no IS NOT NULL
  AND old_req.status IN ('review','approved','processing');
