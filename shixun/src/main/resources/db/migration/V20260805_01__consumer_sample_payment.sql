-- C端打样申请的服务端费用目录与审核后支付状态。
-- 金额单位为人民币元；客户端只能选择产品，不能提交价格。

CREATE TABLE IF NOT EXISTS consumer_sample_fee_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(120) NOT NULL UNIQUE,
    fee_yuan DECIMAL(10,2) NOT NULL,
    source_file VARCHAR(255) NOT NULL DEFAULT '工作簿2.xlsx',
    source_sheet VARCHAR(120) NOT NULL DEFAULT 'Sheet1',
    active TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sample_fee_active (active)
) COMMENT='打样费用目录（服务端定价）';

INSERT IGNORE INTO consumer_sample_fee_catalog(product_name, fee_yuan) VALUES
('合金冰箱贴', 2000),
('胸针/徽章', 1300),
('慕斯蛋糕', 2500),
('亚克力冰箱贴', 1000),
('针织包', 1000),
('马卡龙', 2500),
('树脂冰箱贴', 2500),
('帆布包', 500),
('曲奇饼干', 2500),
('陶瓷冰箱贴', 2000),
('摇摇笔', 1200),
('毛绒', 2000),
('橡皮', 1000),
('搪胶脸毛绒', 5000),
('服饰', 800),
('毛绒挂件', 2000),
('保温杯', 1000),
('金属挂件', 2000),
('笔记本', 1000),
('树脂摆件', 3000),
('磁吸笔记本', 2500),
('亚克力摆件', 1000),
('冰淇淋', 2000),
('叶雕灯', 1000),
('棒棒糖', 2000),
('考古挖掘盲盒', 3500),
('巧克力', 2000);

ALTER TABLE consumer_production_request ADD COLUMN sample_product_name VARCHAR(120) NULL;
ALTER TABLE consumer_production_request ADD COLUMN sample_fee_yuan DECIMAL(10,2) NULL;
ALTER TABLE consumer_production_request ADD COLUMN sample_payment_status VARCHAR(24) NOT NULL DEFAULT 'not_required';
ALTER TABLE consumer_production_request ADD COLUMN sample_payment_order_no VARCHAR(64) NULL;
ALTER TABLE consumer_production_request ADD COLUMN sample_paid_at DATETIME NULL;

