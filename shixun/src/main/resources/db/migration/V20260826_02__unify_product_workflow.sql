-- Unified product identity and the customer sample-quote fields used by both
-- multi-view and 3D model review.  Every statement is guarded so an upgrade
-- can be rerun safely on an existing installation.
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='digital_asset' AND column_name='product_no'),'SELECT 1','ALTER TABLE digital_asset ADD COLUMN product_no VARCHAR(40) NULL AFTER asset_no'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle' AND column_name='product_no'),'SELECT 1','ALTER TABLE creative_multiview_bundle ADD COLUMN product_no VARCHAR(40) NULL AFTER bundle_no'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='product_no'),'SELECT 1','ALTER TABLE consumer_production_request ADD COLUMN product_no VARCHAR(40) NULL AFTER request_no'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_professional_submission' AND column_name='product_no'),'SELECT 1','ALTER TABLE consumer_professional_submission ADD COLUMN product_no VARCHAR(40) NULL AFTER submission_no'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='sample_lead_time'),'SELECT 1','ALTER TABLE consumer_production_request ADD COLUMN sample_lead_time VARCHAR(120) NULL AFTER sample_fee_yuan'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='sample_material'),'SELECT 1','ALTER TABLE consumer_production_request ADD COLUMN sample_material VARCHAR(180) NULL AFTER sample_lead_time'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='sample_quote_note'),'SELECT 1','ALTER TABLE consumer_production_request ADD COLUMN sample_quote_note VARCHAR(1200) NULL AFTER sample_material'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND index_name='idx_cpr_product_no'),'SELECT 1','CREATE INDEX idx_cpr_product_no ON consumer_production_request(product_no,request_type,status)'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Backfill stable product numbers.  Parent assets (for example a generated
-- model derived from an image) inherit the root asset number.
UPDATE digital_asset SET product_no=CONCAT('PRD-',LPAD(COALESCE(parent_asset_id,id),10,'0')) WHERE product_no IS NULL OR product_no='';
UPDATE creative_multiview_bundle b JOIN digital_asset a ON a.id=b.input_asset_id SET b.product_no=COALESCE(b.product_no,a.product_no,CONCAT('PRD-',LPAD(a.id,10,'0'))) WHERE b.product_no IS NULL OR b.product_no='';
UPDATE consumer_production_request r LEFT JOIN creative_multiview_bundle b ON b.id=r.multiview_bundle_id LEFT JOIN digital_asset a ON a.id=r.asset_id SET r.product_no=COALESCE(r.product_no,b.product_no,a.product_no,CONCAT('PRD-',LPAD(COALESCE(r.asset_id,r.id),10,'0'))) WHERE r.product_no IS NULL OR r.product_no='';
UPDATE consumer_professional_submission SET product_no=CONCAT('PRD-',submission_no) WHERE product_no IS NULL OR product_no='';

-- Preserve the newest historical request and mark older repeated taps as
-- duplicates instead of deleting audit history.  New submissions are blocked
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
