-- Repair canonical product numbers after the conversational workflow began
-- creating chained derived assets.  Older code assigned PRD-<immediate
-- parent>, so a reference image, generated image, triptych and GLB could be
-- displayed as different products.  Resolve each asset to its root first,
-- then propagate that root number to every workflow record.

DROP TEMPORARY TABLE IF EXISTS tmp_asset_product_root;
CREATE TEMPORARY TABLE tmp_asset_product_root (
    asset_id BIGINT NOT NULL PRIMARY KEY,
    root_id BIGINT NOT NULL,
    root_product_no VARCHAR(40) NULL
);

-- Materialise the recursive result before updating digital_asset.  MySQL
-- refuses to reopen a table that is both the UPDATE target and a recursive
-- CTE source, while a temporary mapping table avoids that limitation.
INSERT INTO tmp_asset_product_root (asset_id, root_id, root_product_no)
WITH RECURSIVE asset_chain (asset_id, root_id, parent_id, depth) AS (
    SELECT id, id, parent_asset_id, 0
    FROM digital_asset
    UNION ALL
    SELECT c.asset_id, p.id, p.parent_asset_id, c.depth + 1
    FROM asset_chain c
    JOIN digital_asset p ON p.id = c.parent_id
    WHERE c.depth < 64
), asset_roots AS (
    SELECT asset_id, root_id
    FROM asset_chain
    WHERE parent_id IS NULL
)
SELECT r.asset_id, r.root_id, root.product_no
FROM asset_roots r
JOIN digital_asset root ON root.id = r.root_id;

UPDATE digital_asset a
JOIN tmp_asset_product_root r ON r.asset_id = a.id
SET a.product_no = COALESCE(
    NULLIF(r.root_product_no, ''),
    CONCAT('PRD-', LPAD(r.root_id, 10, '0'))
);

-- A bundle represents the same product as its input image, regardless of the
-- generated triptych/simulation child assets attached to it.
UPDATE creative_multiview_bundle b
JOIN digital_asset a ON a.id = b.input_asset_id
SET b.product_no = COALESCE(NULLIF(a.product_no, ''), b.product_no)
WHERE b.input_asset_id IS NOT NULL;

-- Production requests prefer the bundle identity, then the linked asset.  A
-- legacy request without either link keeps its existing number or receives a
-- deterministic fallback from its asset/request id.
UPDATE consumer_production_request r
LEFT JOIN creative_multiview_bundle b ON b.id = r.multiview_bundle_id
LEFT JOIN digital_asset a ON a.id = r.asset_id
SET r.product_no = COALESCE(
    NULLIF(b.product_no, ''),
    NULLIF(a.product_no, ''),
    NULLIF(r.product_no, ''),
    CONCAT('PRD-', LPAD(COALESCE(r.asset_id, r.id), 10, '0'))
);

-- Other commercial records already carry an asset link.  Keep their current
-- value only when the linked asset is unavailable (legacy/orphaned rows).
UPDATE creative_quote_request q
JOIN digital_asset a ON a.id = q.asset_id
SET q.product_no = COALESCE(NULLIF(a.product_no, ''), q.product_no)
WHERE q.asset_id IS NOT NULL;

UPDATE creative_consignment_application c
JOIN digital_asset a ON a.id = c.asset_id
SET c.product_no = COALESCE(NULLIF(a.product_no, ''), c.product_no)
WHERE c.asset_id IS NOT NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_asset_product_root;
