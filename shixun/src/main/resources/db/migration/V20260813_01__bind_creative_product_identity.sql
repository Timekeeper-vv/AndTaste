-- Keep the selected catalog product as the single source of truth through
-- image generation, 3D generation and commercial requests.

-- MySQL 8.0.46 has no ADD COLUMN IF NOT EXISTS. These metadata checks make
-- the migration resumable on installations with a partially created schema.
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND column_name = 'product_key'),
    'SELECT 1',
    'ALTER TABLE ai_generation_job ADD COLUMN product_key VARCHAR(80) NULL AFTER input_asset_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND column_name = 'product_name'),
    'SELECT 1',
    'ALTER TABLE ai_generation_job ADD COLUMN product_name VARCHAR(160) NULL AFTER product_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND column_name = 'product_material'),
    'SELECT 1',
    'ALTER TABLE ai_generation_job ADD COLUMN product_material VARCHAR(500) NULL AFTER product_name'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_generation_job' AND index_name = 'idx_ai_job_product_key'),
    'SELECT 1',
    'CREATE INDEX idx_ai_job_product_key ON ai_generation_job (product_key)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Earlier releases only seeded a small number of product templates. Add any
-- approved selection direction that is still missing, without changing an
-- existing template or inventing a supplier price.
INSERT INTO creative_product_template (
    template_code, selection_option_id, product_name, product_type, material, process,
    specification, sample_moq, bulk_moq, sample_fee_yuan, indicative_retail_display,
    sample_lead_time, bulk_lead_time, supply_status, fulfillment_mode,
    copyright_requirement, published, sort_order
)
SELECT
    CONCAT('catalog-', o.id), o.id, o.name, o.category_key, o.material, o.process,
    o.specification, 1, 50, NULL, COALESCE(NULLIF(o.retail_display, ''), '待运营确认'),
    COALESCE(NULLIF(o.sample_lead_time, ''), '待确认'), COALESCE(NULLIF(o.bulk_lead_time, ''), '待确认'),
    'pending_confirmation', 'preorder',
    '用户须确认拥有原创权、合法授权或公共领域依据；食品、贵金属、品牌联名和博物馆 IP 另需资质与授权核验。',
    1, o.sort_order
FROM selection_option o
WHERE o.enabled = 1
  AND o.review_status = 'approved'
  AND NOT EXISTS (SELECT 1 FROM creative_product_template p WHERE p.selection_option_id = o.id);
