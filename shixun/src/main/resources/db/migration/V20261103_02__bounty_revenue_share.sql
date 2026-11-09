-- 悬赏入选作品的销售分成台账：每个入选投稿一条主记录，配套销售/结算流水明细。

CREATE TABLE IF NOT EXISTS consumer_bounty_revenue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    share_rate DECIMAL(5,2) NOT NULL DEFAULT 10.00 COMMENT '销售分成比例(%)',
    total_sales DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计录入销售额',
    settled_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '已结算给作者的分成金额',
    status VARCHAR(24) NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bounty_revenue_user (user_id, status)
) COMMENT='悬赏入选作品销售分成台账';

CREATE TABLE IF NOT EXISTS consumer_bounty_revenue_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    revenue_id BIGINT NOT NULL,
    kind VARCHAR(20) NOT NULL COMMENT 'sales=销售录入 settlement=分成结算',
    amount DECIMAL(12,2) NOT NULL,
    note VARCHAR(500) NULL,
    created_by VARCHAR(80) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_bounty_revenue_ledger (revenue_id, created_at)
) COMMENT='悬赏销售分成流水明细';