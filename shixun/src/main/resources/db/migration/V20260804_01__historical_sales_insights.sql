-- 2026 年历史项目销量导入。
-- 这是经营分析事实表，不是用户订单，不参与库存扣减、支付和收益结算。
CREATE TABLE IF NOT EXISTS historical_sales_fact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    import_batch VARCHAR(120) NOT NULL,
    source_file VARCHAR(255) NOT NULL,
    source_sheet VARCHAR(120) NOT NULL,
    source_row_no INT NOT NULL,
    report_year SMALLINT NOT NULL,
    project_name VARCHAR(180),
    product_code VARCHAR(120),
    product_name VARCHAR(240) NOT NULL,
    product_type VARCHAR(100),
    secondary_type VARCHAR(100),
    sales_jan INT NOT NULL DEFAULT 0,
    sales_feb INT NOT NULL DEFAULT 0,
    sales_mar INT NOT NULL DEFAULT 0,
    sales_apr INT NOT NULL DEFAULT 0,
    sales_may INT NOT NULL DEFAULT 0,
    sales_jun INT NOT NULL DEFAULT 0,
    sales_jul INT NOT NULL DEFAULT 0,
    sales_ytd INT NOT NULL DEFAULT 0,
    loss_ytd INT NOT NULL DEFAULT 0,
    imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_historical_sales_source (import_batch, source_row_no),
    KEY idx_historical_sales_project (project_name),
    KEY idx_historical_sales_product (product_code),
    KEY idx_historical_sales_type (product_type, secondary_type),
    KEY idx_historical_sales_year (report_year)
) COMMENT='历史项目销量分析事实，不作为交易订单';

