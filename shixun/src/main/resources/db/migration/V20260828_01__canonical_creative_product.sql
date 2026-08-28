-- Canonical product identity for every creative workflow.
--
-- product_no is the human-facing identifier.  product_id is the stable
-- internal link to creative_product.  Asset, bundle, request, submission and
-- payment table primary keys remain independent audit identifiers.

CREATE TABLE IF NOT EXISTS creative_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_no VARCHAR(40) NOT NULL UNIQUE,
    user_id BIGINT NULL,
    product_name VARCHAR(200) NULL,
    product_key VARCHAR(120) NULL,
    category VARCHAR(100) NULL,
    material VARCHAR(180) NULL,
    product_size VARCHAR(120) NULL,
    current_version_id BIGINT NULL,
    lifecycle_status VARCHAR(30) NOT NULL DEFAULT 'active' COMMENT 'active/archived/closed',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cp_user (user_id, updated_at),
    INDEX idx_cp_key (product_key),
    INDEX idx_cp_status (lifecycle_status, updated_at)
) COMMENT='Canonical product identity shared by all creative assets and orders';

-- MySQL 8 does not support ADD COLUMN IF NOT EXISTS.  Keep each additive
-- change resumable so a rolling deployment can be retried safely.
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='digital_asset')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='digital_asset' AND column_name='product_id'),
    'ALTER TABLE digital_asset ADD COLUMN product_id BIGINT NULL AFTER product_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle' AND column_name='product_id'),
    'ALTER TABLE creative_multiview_bundle ADD COLUMN product_id BIGINT NULL AFTER product_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='product_id'),
    'ALTER TABLE consumer_production_request ADD COLUMN product_id BIGINT NULL AFTER product_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_professional_submission')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_professional_submission' AND column_name='product_id'),
    'ALTER TABLE consumer_professional_submission ADD COLUMN product_id BIGINT NULL AFTER product_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_quote_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_quote_request' AND column_name='product_id'),
    'ALTER TABLE creative_quote_request ADD COLUMN product_id BIGINT NULL AFTER product_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_consignment_application')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_consignment_application' AND column_name='product_id'),
    'ALTER TABLE creative_consignment_application ADD COLUMN product_id BIGINT NULL AFTER product_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Guidance and payment rows are also part of the product lifecycle.  Their
-- fields are nullable so old credit-package orders remain valid.
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='commercial_professional_guidance_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='commercial_professional_guidance_request' AND column_name='product_no'),
    'ALTER TABLE commercial_professional_guidance_request ADD COLUMN product_no VARCHAR(40) NULL AFTER guidance_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='commercial_professional_guidance_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='commercial_professional_guidance_request' AND column_name='product_id'),
    'ALTER TABLE commercial_professional_guidance_request ADD COLUMN product_id BIGINT NULL AFTER product_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='payment_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='payment_order' AND column_name='product_no'),
    'ALTER TABLE payment_order ADD COLUMN product_no VARCHAR(40) NULL AFTER product_code',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='payment_order')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='payment_order' AND column_name='product_id'),
    'ALTER TABLE payment_order ADD COLUMN product_id BIGINT NULL AFTER product_no',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Repair numbers for rows that were created before the product identity
-- migrations.  Linked assets/bundles always win; orphan rows use a typed,
-- deterministic business number rather than a database id collision.
UPDATE digital_asset
SET product_no=CONCAT('PRD-',LPAD(id,10,'0'))
WHERE product_no IS NULL OR product_no='';

UPDATE creative_multiview_bundle b
LEFT JOIN digital_asset a ON a.id=b.input_asset_id
SET b.product_no=COALESCE(NULLIF(a.product_no,''), NULLIF(b.product_no,''), CONCAT('PRD-MVB-',LEFT(b.bundle_no,31)))
WHERE b.product_no IS NULL OR b.product_no='';

UPDATE consumer_production_request r
LEFT JOIN creative_multiview_bundle b ON b.id=r.multiview_bundle_id
LEFT JOIN digital_asset a ON a.id=r.asset_id
SET r.product_no=COALESCE(NULLIF(b.product_no,''), NULLIF(a.product_no,''), NULLIF(r.product_no,''), CONCAT('PRD-CPR-',LEFT(r.request_no,31)))
WHERE r.product_no IS NULL OR r.product_no=''
   OR (b.product_no IS NOT NULL AND b.product_no<>'' AND r.product_no<>b.product_no)
   OR (b.product_no IS NULL AND a.product_no IS NOT NULL AND a.product_no<>'' AND r.product_no<>a.product_no);

UPDATE consumer_professional_submission
SET product_no=CONCAT('PRD-CPS-',LEFT(submission_no,31))
WHERE product_no IS NULL OR product_no='';

UPDATE creative_quote_request q
LEFT JOIN digital_asset a ON a.id=q.asset_id
SET q.product_no=COALESCE(NULLIF(a.product_no,''), NULLIF(q.product_no,''), CONCAT('PRD-CQR-',LEFT(q.request_no,31)))
WHERE q.product_no IS NULL OR q.product_no=''
   OR (a.product_no IS NOT NULL AND a.product_no<>'' AND q.product_no<>a.product_no);

UPDATE creative_consignment_application c
LEFT JOIN digital_asset a ON a.id=c.asset_id
SET c.product_no=COALESCE(NULLIF(a.product_no,''), NULLIF(c.product_no,''), CONCAT('PRD-CCA-',LEFT(c.application_no,31)))
WHERE c.product_no IS NULL OR c.product_no=''
   OR (a.product_no IS NOT NULL AND a.product_no<>'' AND c.product_no<>a.product_no);

-- One row in creative_product represents one product number.  The source
-- union intentionally includes every workflow table so a product remains
-- visible even if its original image has been archived.
INSERT INTO creative_product (product_no,user_id,product_name,product_key,category,material,product_size)
SELECT source.product_no,
       MAX(source.user_id),
       MAX(NULLIF(source.product_name,'')),
       MAX(NULLIF(source.product_key,'')),
       MAX(NULLIF(source.category,'')),
       MAX(NULLIF(source.material,'')),
       MAX(NULLIF(source.product_size,''))
FROM (
    SELECT NULLIF(TRIM(a.product_no),'') product_no,a.created_by user_id,a.title product_name,
           NULL product_key,NULL category,NULL material,NULL product_size
    FROM digital_asset a
    UNION ALL
    SELECT NULLIF(TRIM(b.product_no),'') product_no,b.user_id,b.product_name,b.product_key,
           NULL,b.material,b.product_size
    FROM creative_multiview_bundle b
    UNION ALL
    SELECT NULLIF(TRIM(r.product_no),'') product_no,r.user_id,
           COALESCE(NULLIF(r.sample_product_name,''),NULLIF(r.title,'')) product_name,
           NULL,NULL,NULL,NULL
    FROM consumer_production_request r
    UNION ALL
    SELECT NULLIF(TRIM(s.product_no),'') product_no,s.user_id,s.title,NULL,NULL,NULL,NULL
    FROM consumer_professional_submission s
    UNION ALL
    SELECT NULLIF(TRIM(q.product_no),'') product_no,q.user_id,p.product_name,p.template_code,
           p.product_type,p.material,NULL
    FROM creative_quote_request q
    LEFT JOIN creative_product_template p ON p.id=q.product_template_id
    UNION ALL
    SELECT NULLIF(TRIM(c.product_no),'') product_no,c.user_id,p.product_name,p.template_code,
           p.product_type,p.material,NULL
    FROM creative_consignment_application c
    LEFT JOIN creative_product_template p ON p.id=c.product_template_id
) source
WHERE source.product_no IS NOT NULL AND source.product_no<>''
GROUP BY source.product_no
ON DUPLICATE KEY UPDATE
    user_id=COALESCE(creative_product.user_id,VALUES(user_id)),
    product_name=COALESCE(creative_product.product_name,VALUES(product_name)),
    product_key=COALESCE(creative_product.product_key,VALUES(product_key)),
    category=COALESCE(creative_product.category,VALUES(category)),
    material=COALESCE(creative_product.material,VALUES(material)),
    product_size=COALESCE(creative_product.product_size,VALUES(product_size)),
    updated_at=CURRENT_TIMESTAMP;

-- Attach every row to the canonical product.  No foreign key is added here:
-- legacy installations can contain archived/orphan asset references, while
-- the unique product_no and these indexes still provide a stable join path.
UPDATE digital_asset a JOIN creative_product p ON p.product_no=a.product_no
SET a.product_id=p.id
WHERE a.product_id IS NULL OR a.product_id<>p.id;

UPDATE creative_multiview_bundle b JOIN creative_product p ON p.product_no=b.product_no
SET b.product_id=p.id
WHERE b.product_id IS NULL OR b.product_id<>p.id;

UPDATE consumer_production_request r JOIN creative_product p ON p.product_no=r.product_no
SET r.product_id=p.id
WHERE r.product_id IS NULL OR r.product_id<>p.id;

UPDATE consumer_professional_submission s JOIN creative_product p ON p.product_no=s.product_no
SET s.product_id=p.id
WHERE s.product_id IS NULL OR s.product_id<>p.id;

UPDATE creative_quote_request q JOIN creative_product p ON p.product_no=q.product_no
SET q.product_id=p.id
WHERE q.product_id IS NULL OR q.product_id<>p.id;

UPDATE creative_consignment_application c JOIN creative_product p ON p.product_no=c.product_no
SET c.product_id=p.id
WHERE c.product_id IS NULL OR c.product_id<>p.id;

UPDATE commercial_professional_guidance_request g
JOIN creative_quote_request q ON g.application_type='quote' AND q.id=g.application_id
JOIN creative_product p ON p.product_no=q.product_no
SET g.product_no=q.product_no,g.product_id=p.id
WHERE g.product_no IS NULL OR g.product_no='' OR g.product_id IS NULL OR g.product_id<>p.id;

UPDATE commercial_professional_guidance_request g
JOIN creative_consignment_application c ON g.application_type='consignment' AND c.id=g.application_id
JOIN creative_product p ON p.product_no=c.product_no
SET g.product_no=c.product_no,g.product_id=p.id
WHERE g.product_no IS NULL OR g.product_no='' OR g.product_id IS NULL OR g.product_id<>p.id;

-- Payment codes are generated from the business row ids.  Link all sample,
-- commercial quote and professional ZIP payments; recharge orders correctly
-- remain without a product identity.
UPDATE payment_order po
JOIN consumer_production_request r ON po.product_code=CONCAT('sample_fee_',r.id)
JOIN creative_product p ON p.product_no=r.product_no
SET po.product_no=r.product_no,po.product_id=p.id
WHERE po.product_no IS NULL OR po.product_no='' OR po.product_id IS NULL OR po.product_id<>p.id;

UPDATE payment_order po
JOIN creative_quote_request q ON po.product_code=CONCAT('commercial_quote_sample_',q.id)
JOIN creative_product p ON p.product_no=q.product_no
SET po.product_no=q.product_no,po.product_id=p.id
WHERE po.product_no IS NULL OR po.product_no='' OR po.product_id IS NULL OR po.product_id<>p.id;

UPDATE payment_order po
JOIN consumer_professional_submission s ON po.product_code=CONCAT('professional_submission_sample_',s.id)
JOIN creative_product p ON p.product_no=s.product_no
SET po.product_no=s.product_no,po.product_id=p.id
WHERE po.product_no IS NULL OR po.product_no='' OR po.product_id IS NULL OR po.product_id<>p.id;

UPDATE payment_order po
JOIN commercial_professional_guidance_request g ON po.product_code=CONCAT('commercial_guidance_',g.id)
JOIN creative_product p ON p.id=g.product_id
SET po.product_no=g.product_no,po.product_id=p.id
WHERE po.product_no IS NULL OR po.product_no='' OR po.product_id IS NULL OR po.product_id<>p.id;

-- Indexes are additive and named explicitly to make the migration safe to
-- retry after an interrupted deployment.
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='digital_asset') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='digital_asset' AND column_name='product_id') AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='digital_asset' AND index_name='idx_asset_product_id'), 'CREATE INDEX idx_asset_product_id ON digital_asset(product_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle' AND column_name='product_id') AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='creative_multiview_bundle' AND index_name='idx_cmb_product_id'), 'CREATE INDEX idx_cmb_product_id ON creative_multiview_bundle(product_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_production_request') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND column_name='product_id') AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='consumer_production_request' AND index_name='idx_cpr_product_id'), 'CREATE INDEX idx_cpr_product_id ON consumer_production_request(product_id,request_type,status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='consumer_professional_submission') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consumer_professional_submission' AND column_name='product_id') AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='consumer_professional_submission' AND index_name='idx_cps_product_id'), 'CREATE INDEX idx_cps_product_id ON consumer_professional_submission(product_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_quote_request') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_quote_request' AND column_name='product_id') AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='creative_quote_request' AND index_name='idx_cqr_product_id'), 'CREATE INDEX idx_cqr_product_id ON creative_quote_request(product_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='creative_consignment_application') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='creative_consignment_application' AND column_name='product_id') AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='creative_consignment_application' AND index_name='idx_cca_product_id'), 'CREATE INDEX idx_cca_product_id ON creative_consignment_application(product_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='commercial_professional_guidance_request') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='commercial_professional_guidance_request' AND column_name='product_id') AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='commercial_professional_guidance_request' AND index_name='idx_cpgr_product_id'), 'CREATE INDEX idx_cpgr_product_id ON commercial_professional_guidance_request(product_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='payment_order') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='payment_order' AND column_name='product_id') AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='payment_order' AND index_name='idx_payment_product_id'), 'CREATE INDEX idx_payment_product_id ON payment_order(product_id,created_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
