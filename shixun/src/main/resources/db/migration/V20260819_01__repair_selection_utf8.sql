-- Repair catalog rows imported by older deployment scripts without an
-- explicit client character set. Those rows contain UTF-8 bytes decoded as
-- latin1 (for example, 曲奇糕点 became æ›²å¥‡ç³•ç‚¹).
-- The marker predicate makes this migration safe to run more than once.
SET NAMES utf8mb4;

UPDATE selection_category
SET name = CONVERT(BINARY CONVERT(name USING latin1) USING utf8mb4),
    description = CONVERT(BINARY CONVERT(description USING latin1) USING utf8mb4),
    source_name = CONVERT(BINARY CONVERT(source_name USING latin1) USING utf8mb4)
WHERE name REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR description REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR source_name REGEXP '[ÃÂÆæÅåÇçÈèÉé]';

UPDATE selection_option
SET name = CONVERT(BINARY CONVERT(name USING latin1) USING utf8mb4),
    subtitle = CONVERT(BINARY CONVERT(subtitle USING latin1) USING utf8mb4),
    description = CONVERT(BINARY CONVERT(description USING latin1) USING utf8mb4),
    material = CONVERT(BINARY CONVERT(material USING latin1) USING utf8mb4),
    process = CONVERT(BINARY CONVERT(process USING latin1) USING utf8mb4),
    specification = CONVERT(BINARY CONVERT(specification USING latin1) USING utf8mb4),
    sample_lead_time = CONVERT(BINARY CONVERT(sample_lead_time USING latin1) USING utf8mb4),
    bulk_lead_time = CONVERT(BINARY CONVERT(bulk_lead_time USING latin1) USING utf8mb4),
    retail_display = CONVERT(BINARY CONVERT(retail_display USING latin1) USING utf8mb4),
    tags = CONVERT(BINARY CONVERT(tags USING latin1) USING utf8mb4),
    audience_tags = CONVERT(BINARY CONVERT(audience_tags USING latin1) USING utf8mb4),
    occasion_tags = CONVERT(BINARY CONVERT(occasion_tags USING latin1) USING utf8mb4),
    image_source = CONVERT(BINARY CONVERT(image_source USING latin1) USING utf8mb4),
    source_name = CONVERT(BINARY CONVERT(source_name USING latin1) USING utf8mb4)
WHERE name REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR subtitle REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR description REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR material REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR process REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR specification REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR sample_lead_time REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR bulk_lead_time REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR retail_display REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR tags REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR audience_tags REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR occasion_tags REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR image_source REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR source_name REGEXP '[ÃÂÆæÅåÇçÈèÉé]';

UPDATE creative_product_template
SET product_name = CONVERT(BINARY CONVERT(product_name USING latin1) USING utf8mb4),
    product_type = CONVERT(BINARY CONVERT(product_type USING latin1) USING utf8mb4),
    material = CONVERT(BINARY CONVERT(material USING latin1) USING utf8mb4),
    process = CONVERT(BINARY CONVERT(process USING latin1) USING utf8mb4),
    specification = CONVERT(BINARY CONVERT(specification USING latin1) USING utf8mb4),
    indicative_retail_display = CONVERT(BINARY CONVERT(indicative_retail_display USING latin1) USING utf8mb4),
    sample_lead_time = CONVERT(BINARY CONVERT(sample_lead_time USING latin1) USING utf8mb4),
    bulk_lead_time = CONVERT(BINARY CONVERT(bulk_lead_time USING latin1) USING utf8mb4),
    copyright_requirement = CONVERT(BINARY CONVERT(copyright_requirement USING latin1) USING utf8mb4)
WHERE product_name REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR product_type REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR material REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR process REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR specification REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR indicative_retail_display REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR sample_lead_time REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR bulk_lead_time REGEXP '[ÃÂÆæÅåÇçÈèÉé]'
   OR copyright_requirement REGEXP '[ÃÂÆæÅåÇçÈèÉé]';
