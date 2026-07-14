-- 之间味道：图片类文创设计售卖平台 Schema
-- 原则：IP（artwork）与实体商品 SKU（product_sku）分离，支持设计师、订单、收藏、评价、收益分成扩展。

CREATE DATABASE IF NOT EXISTS shixun DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE shixun;

-- 平台用户：消费者、设计师、运营后台统一账号表
CREATE TABLE IF NOT EXISTS platform_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE COMMENT '登录用户名',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt 或兼容密码',
    display_name VARCHAR(100) COMMENT '展示昵称',
    email VARCHAR(200),
    phone VARCHAR(30),
    role VARCHAR(30) NOT NULL DEFAULT 'consumer' COMMENT 'consumer/designer/operator/admin',
    avatar_url VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'active' COMMENT 'active/disabled/pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='之间味道平台用户';

CREATE TABLE IF NOT EXISTS designer_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    brand_name VARCHAR(120) NOT NULL COMMENT '设计师/工作室名称',
    bio TEXT COMMENT '设计理念与简介',
    portfolio_url VARCHAR(500),
    settlement_account VARCHAR(255) COMMENT '结算账号，可后续加密',
    revenue_share DECIMAL(5,2) NOT NULL DEFAULT 70.00 COMMENT '设计师分成比例%',
    audit_status VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_designer_user FOREIGN KEY (user_id) REFERENCES platform_user(id)
) COMMENT='设计师/创作者档案';

CREATE TABLE IF NOT EXISTS category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE COMMENT '分类：国风/治愈系/地域文化等',
    description VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='图片IP分类';

CREATE TABLE IF NOT EXISTS tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(60) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='图片IP标签';

-- 图片IP/作品：数字资产本体
CREATE TABLE IF NOT EXISTS artwork (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    subtitle VARCHAR(255),
    designer_id BIGINT,
    category_id BIGINT,
    image_url VARCHAR(800) NOT NULL COMMENT '作品主图/高清图 URL',
    thumbnail_url VARCHAR(800),
    story TEXT COMMENT '设计理念/作品故事',
    license_type VARCHAR(50) NOT NULL DEFAULT 'platform_sale' COMMENT '授权类型',
    audit_status VARCHAR(30) NOT NULL DEFAULT 'approved' COMMENT 'draft/pending/approved/rejected',
    sale_status VARCHAR(30) NOT NULL DEFAULT 'on_sale' COMMENT 'on_sale/off_sale',
    view_count INT NOT NULL DEFAULT 0,
    favorite_count INT NOT NULL DEFAULT 0,
    extension_json JSON NULL COMMENT '预留：数字藏品、线下展览、联名信息等',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_artwork_designer FOREIGN KEY (designer_id) REFERENCES designer_profile(id),
    CONSTRAINT fk_artwork_category FOREIGN KEY (category_id) REFERENCES category(id)
) COMMENT='图片IP/设计作品';

CREATE TABLE IF NOT EXISTS artwork_tag (
    artwork_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (artwork_id, tag_id),
    CONSTRAINT fk_artwork_tag_artwork FOREIGN KEY (artwork_id) REFERENCES artwork(id) ON DELETE CASCADE,
    CONSTRAINT fk_artwork_tag_tag FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
) COMMENT='作品标签关联';

-- 文创实体商品 SKU：一张图片IP可衍生多个SKU
CREATE TABLE IF NOT EXISTS product_sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    artwork_id BIGINT NOT NULL,
    sku_code VARCHAR(80) NOT NULL UNIQUE,
    product_name VARCHAR(180) NOT NULL COMMENT '如：故乡风物明信片套装',
    product_type VARCHAR(60) NOT NULL COMMENT 'postcard/print/phone_case/canvas_bag/sticker/custom',
    cover_url VARCHAR(800),
    price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    original_price DECIMAL(10,2),
    stock INT NOT NULL DEFAULT 0,
    material VARCHAR(100) COMMENT '纸张/亚克力/帆布等',
    size VARCHAR(100) COMMENT '尺寸',
    options_json JSON NULL COMMENT '定制选项：尺寸、材质、签名版等',
    status VARCHAR(30) NOT NULL DEFAULT 'on_sale' COMMENT 'on_sale/off_sale/sold_out',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sku_artwork FOREIGN KEY (artwork_id) REFERENCES artwork(id)
) COMMENT='文创商品SKU';

CREATE TABLE IF NOT EXISTS address (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receiver_name VARCHAR(80) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    province VARCHAR(80),
    city VARCHAR(80),
    district VARCHAR(80),
    detail VARCHAR(255) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES platform_user(id)
) COMMENT='收货地址';

CREATE TABLE IF NOT EXISTS shopping_cart_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    custom_options_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cart_user_sku (user_id, sku_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES platform_user(id),
    CONSTRAINT fk_cart_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id)
) COMMENT='购物车';

CREATE TABLE IF NOT EXISTS `order` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    address_id BIGINT,
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(30) COMMENT 'wechat/alipay/mock',
    order_status VARCHAR(30) NOT NULL DEFAULT 'pending_pay' COMMENT 'pending_pay/paid/producing/shipped/completed/cancelled/refunded',
    remark VARCHAR(500),
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES platform_user(id),
    CONSTRAINT fk_order_address FOREIGN KEY (address_id) REFERENCES address(id)
) COMMENT='订单主表';

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    artwork_id BIGINT NOT NULL,
    product_name VARCHAR(180) NOT NULL,
    artwork_title VARCHAR(160) NOT NULL,
    cover_url VARCHAR(800),
    unit_price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    custom_options_json JSON NULL,
    designer_id BIGINT,
    designer_revenue DECIMAL(10,2) DEFAULT 0.00,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES `order`(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id),
    CONSTRAINT fk_order_item_artwork FOREIGN KEY (artwork_id) REFERENCES artwork(id),
    CONSTRAINT fk_order_item_designer FOREIGN KEY (designer_id) REFERENCES designer_profile(id)
) COMMENT='订单明细';

CREATE TABLE IF NOT EXISTS favorite_artwork (
    user_id BIGINT NOT NULL,
    artwork_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, artwork_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES platform_user(id),
    CONSTRAINT fk_favorite_artwork FOREIGN KEY (artwork_id) REFERENCES artwork(id) ON DELETE CASCADE
) COMMENT='作品收藏';

CREATE TABLE IF NOT EXISTS product_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    order_item_id BIGINT,
    rating INT NOT NULL DEFAULT 5,
    content VARCHAR(1000),
    image_urls JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES platform_user(id),
    CONSTRAINT fk_review_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id),
    CONSTRAINT fk_review_order_item FOREIGN KEY (order_item_id) REFERENCES order_item(id)
) COMMENT='商品评价';

-- 样本数据：可重复执行
INSERT IGNORE INTO platform_user (id, username, password, display_name, email, phone, role, status) VALUES
(1, 'admin', '123456', '平台运营', 'admin@andtaste.com', '13800000001', 'admin', 'active'),
(2, 'designer01', '123456', '山间造物', 'designer@andtaste.com', '13800000002', 'designer', 'active'),
(3, 'consumer01', '123456', '味道收藏家', 'buyer@andtaste.com', '13800000003', 'consumer', 'active');

INSERT IGNORE INTO designer_profile (id, user_id, brand_name, bio, revenue_share, audit_status) VALUES
(1, 2, '山间造物工作室', '以地域文化、自然风物和生活记忆为核心，创作具有温度的图片IP。', 70.00, 'approved');

INSERT IGNORE INTO category (id, name, description, sort_order) VALUES
(1, '国风', '传统纹样、东方美学、古典意象', 1),
(2, '治愈系', '温暖日常、轻松松弛、柔和色彩', 2),
(3, '地域文化', '城市记忆、地方风物、旅行纪念', 3),
(4, '节日限定', '春节、中秋、节气、纪念日主题', 4);

INSERT IGNORE INTO tag (id, name) VALUES
(1, '插画'), (2, '海报'), (3, '明信片'), (4, '手账'), (5, '东方美学'), (6, '城市礼物');

INSERT IGNORE INTO artwork (id, title, subtitle, designer_id, category_id, image_url, thumbnail_url, story, license_type, audit_status, sale_status, view_count, favorite_count) VALUES
(1, '山城晚风', '把城市黄昏装进一张明信片', 1, 3, '/and-taste-sample-1.svg', '/and-taste-sample-1.svg', '灵感来自傍晚街巷的灯光、坡道与风，适合作为旅行纪念与城市礼物。', 'platform_sale', 'approved', 'on_sale', 1280, 96),
(2, '花间小憩', '治愈系春日插画', 1, 2, '/and-taste-sample-2.svg', '/and-taste-sample-2.svg', '用柔和色彩描绘日常片刻，希望每个看到它的人都能慢下来。', 'platform_sale', 'approved', 'on_sale', 860, 72),
(3, '青瓦与月', '东方意象装饰画', 1, 1, '/and-taste-sample-3.svg', '/and-taste-sample-3.svg', '青瓦、月色、花影构成安静的东方场景，可延展为家居装饰与礼品。', 'platform_sale', 'approved', 'on_sale', 1520, 148);

INSERT IGNORE INTO artwork_tag (artwork_id, tag_id) VALUES
(1, 1), (1, 3), (1, 6),
(2, 1), (2, 4),
(3, 2), (3, 5);

INSERT IGNORE INTO product_sku (id, artwork_id, sku_code, product_name, product_type, cover_url, price, original_price, stock, material, size, status, options_json) VALUES
(1, 1, 'AT-PC-001', '山城晚风明信片套装', 'postcard', '/and-taste-sample-1.svg', 29.90, 39.90, 200, '艺术纸', '100mm x 148mm / 8张', 'on_sale', JSON_OBJECT('signed', JSON_ARRAY('普通版','签名版'))),
(2, 1, 'AT-PR-001', '山城晚风装饰画', 'print', '/and-taste-sample-1.svg', 99.00, 129.00, 80, '微喷艺术纸', 'A4/A3 可选', 'on_sale', JSON_OBJECT('size', JSON_ARRAY('A4','A3'), 'frame', JSON_ARRAY('无框','黑色画框'))),
(3, 2, 'AT-ST-002', '花间小憩贴纸包', 'sticker', '/and-taste-sample-2.svg', 19.90, 29.90, 300, '防水PVC', '6枚/包', 'on_sale', NULL),
(4, 3, 'AT-BG-003', '青瓦与月帆布袋', 'canvas_bag', '/and-taste-sample-3.svg', 69.00, 89.00, 120, '加厚帆布', '单肩通勤款', 'on_sale', JSON_OBJECT('color', JSON_ARRAY('米白','黑色')));


-- AI_CREATIVE_LAYER_EXTENSION_V1
-- 第一层：创意设计层（AI赋能前端）

CREATE TABLE IF NOT EXISTS brand_style_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '品牌视觉风格名称',
    description VARCHAR(500),
    base_prompt TEXT NOT NULL COMMENT '品牌基础Prompt',
    negative_prompt TEXT COMMENT '反向Prompt，避免低质/跑偏/文化幻觉',
    palette VARCHAR(200) COMMENT '色彩体系',
    cultural_guardrails TEXT COMMENT '文化语义与符号纠偏规则',
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='品牌专属视觉风格库';

CREATE TABLE IF NOT EXISTS digital_asset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_no VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(180) NOT NULL,
    asset_type VARCHAR(30) NOT NULL COMMENT 'image/model3d/project/prompt',
    source_type VARCHAR(30) NOT NULL DEFAULT 'ai_generated' COMMENT 'ai_generated/upload/manual',
    file_url VARCHAR(800),
    preview_url VARCHAR(800),
    prompt TEXT,
    negative_prompt TEXT,
    style_id BIGINT,
    version_no INT NOT NULL DEFAULT 1,
    parent_asset_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT 'draft/review/approved/archived',
    format VARCHAR(30) COMMENT 'png/jpg/obj/stl等',
    tags VARCHAR(500),
    metadata_json JSON NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_asset_style FOREIGN KEY (style_id) REFERENCES brand_style_profile(id),
    CONSTRAINT fk_asset_parent FOREIGN KEY (parent_asset_id) REFERENCES digital_asset(id)
) COMMENT='数字资产库DAM：2D/3D/草图/Prompt/项目文件';

CREATE TABLE IF NOT EXISTS ai_generation_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_no VARCHAR(80) NOT NULL UNIQUE,
    job_type VARCHAR(30) NOT NULL COMMENT 'text_to_image/image_to_image/text_to_3d/image_to_3d',
    provider VARCHAR(50) NOT NULL COMMENT 'siliconflow/tripo/mock',
    model_name VARCHAR(120),
    style_id BIGINT NULL,
    input_asset_id BIGINT NULL,
    output_asset_id BIGINT NULL,
    prompt TEXT,
    negative_prompt TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/succeeded/failed',
    error_message TEXT,
    export_formats VARCHAR(120) COMMENT 'OBJ,STL,GLB等',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_style FOREIGN KEY (style_id) REFERENCES brand_style_profile(id),
    CONSTRAINT fk_job_input_asset FOREIGN KEY (input_asset_id) REFERENCES digital_asset(id),
    CONSTRAINT fk_job_output_asset FOREIGN KEY (output_asset_id) REFERENCES digital_asset(id)
) COMMENT='AI生成任务记录';

UPDATE digital_asset SET source_type='upload' WHERE file_url LIKE '/uploads/%';

INSERT IGNORE INTO brand_style_profile (id, name, description, base_prompt, negative_prompt, palette, cultural_guardrails) VALUES
(1, '之间味道 · 东方生活美学', '品牌默认风格：温润、留白、文化符号克制表达，适合明信片、装饰画和礼品包装。',
 'Between Taste brand visual, Chinese contemporary cultural creative design, warm daily life, elegant composition, subtle oriental aesthetics, clean negative space, premium illustration, suitable for postcards and art prints',
 'low quality, blurry, extra fingers, distorted text, random pseudo Chinese characters, cultural stereotype, disrespectful symbol, messy composition, watermark, logo, bad anatomy',
 '米白、墨黑、朱砂橙、青黛、暖金',
 '文化符号必须服务于主题；避免随机堆叠龙凤、佛像、文字等敏感或强语义符号；地域文化需结合具体场景、器物、食物或建筑特征；不要生成不可读伪中文作为主视觉文字。'),
(2, '治愈系日常插画', '适合贴纸、手机壳、手账周边的轻松治愈风格。',
 'soft healing illustration, cozy daily life, gentle colors, cute but refined, clean commercial product design, warm light, minimal background',
 'low quality, noisy, dark horror, aggressive style, messy details, watermark, unreadable text',
 '奶油白、樱花粉、鼠尾草绿、浅蓝',
 '避免过度幼稚化；保持可商品化构图，主体边界清晰，适合衍生为贴纸或手机壳。');


-- AI_REVIEW_PANEL_EXTENSION_V1
-- AI评审团：多智能体从设计、市场、成本、消费者角度评审同一设计资产
CREATE TABLE IF NOT EXISTS design_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_no VARCHAR(80) NOT NULL UNIQUE,
    asset_id BIGINT NOT NULL,
    overall_score DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    summary TEXT,
    recommendation VARCHAR(50) COMMENT 'go/adjust/reject',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_asset FOREIGN KEY (asset_id) REFERENCES digital_asset(id)
) COMMENT='AI评审团汇总评审';

CREATE TABLE IF NOT EXISTS design_review_agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    agent_key VARCHAR(60) NOT NULL,
    agent_name VARCHAR(100) NOT NULL,
    score INT NOT NULL DEFAULT 0,
    verdict VARCHAR(80),
    comments TEXT,
    suggestions_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_agent_review FOREIGN KEY (review_id) REFERENCES design_review(id) ON DELETE CASCADE
) COMMENT='AI评审团单智能体评审结果';


-- PRODUCTION_MANAGEMENT_LAYER_EXTENSION_V1
-- 第二层：生产管理层（BOM与成本驱动）

CREATE TABLE IF NOT EXISTS material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(60) NOT NULL COMMENT 'paper/ink/package/accessory/base_material',
    unit VARCHAR(30) NOT NULL DEFAULT 'pcs',
    standard_cost DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    stock_qty DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    safety_stock DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    supplier VARCHAR(120),
    lead_time_days INT NOT NULL DEFAULT 3,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='标准物料与库存成本';

CREATE TABLE IF NOT EXISTS craft_process (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(60) COMMENT 'printing/coating/cutting/plating/painting/assembly/packing',
    unit VARCHAR(30) NOT NULL DEFAULT 'pcs',
    standard_cost DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    standard_hours DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    loss_rate DECIMAL(6,4) NOT NULL DEFAULT 0.0000 COMMENT '工艺损耗率',
    description VARCHAR(500),
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='标准工艺与加工成本';

CREATE TABLE IF NOT EXISTS product_bom (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bom_no VARCHAR(80) NOT NULL UNIQUE,
    sku_id BIGINT NULL,
    asset_id BIGINT NULL,
    product_name VARCHAR(180) NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    planned_qty INT NOT NULL DEFAULT 100,
    target_price DECIMAL(10,2) DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT 'draft/confirmed/archived',
    remark VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id),
    CONSTRAINT fk_bom_asset FOREIGN KEY (asset_id) REFERENCES digital_asset(id)
) COMMENT='产品BOM主表';

CREATE TABLE IF NOT EXISTS bom_material_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bom_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    qty DECIMAL(12,4) NOT NULL DEFAULT 1.0000,
    loss_rate DECIMAL(6,4) NOT NULL DEFAULT 0.0000,
    unit_cost DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    remark VARCHAR(300),
    CONSTRAINT fk_bom_material_bom FOREIGN KEY (bom_id) REFERENCES product_bom(id) ON DELETE CASCADE,
    CONSTRAINT fk_bom_material_material FOREIGN KEY (material_id) REFERENCES material(id)
) COMMENT='BOM物料明细';

CREATE TABLE IF NOT EXISTS bom_process_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bom_id BIGINT NOT NULL,
    process_id BIGINT NOT NULL,
    qty DECIMAL(12,4) NOT NULL DEFAULT 1.0000,
    unit_cost DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    standard_hours DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    remark VARCHAR(300),
    CONSTRAINT fk_bom_process_bom FOREIGN KEY (bom_id) REFERENCES product_bom(id) ON DELETE CASCADE,
    CONSTRAINT fk_bom_process_process FOREIGN KEY (process_id) REFERENCES craft_process(id)
) COMMENT='BOM工艺明细';

CREATE TABLE IF NOT EXISTS cost_quote (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_no VARCHAR(80) NOT NULL UNIQUE,
    bom_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 100,
    material_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    process_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    packaging_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    overhead_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    unit_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    suggested_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    gross_margin_rate DECIMAL(6,4) NOT NULL DEFAULT 0.0000,
    simulation_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quote_bom FOREIGN KEY (bom_id) REFERENCES product_bom(id)
) COMMENT='成本报价与动态毛利模拟';

CREATE TABLE IF NOT EXISTS sample_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sample_no VARCHAR(80) NOT NULL UNIQUE,
    bom_id BIGINT NOT NULL,
    asset_id BIGINT NULL,
    sample_qty INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT 'pending/in_progress/review_passed/rework/cancelled',
    estimated_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    actual_cost DECIMAL(12,2) DEFAULT 0.00,
    feedback TEXT,
    due_date DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sample_bom FOREIGN KEY (bom_id) REFERENCES product_bom(id),
    CONSTRAINT fk_sample_asset FOREIGN KEY (asset_id) REFERENCES digital_asset(id)
) COMMENT='产品打样管理';

CREATE TABLE IF NOT EXISTS production_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    production_no VARCHAR(80) NOT NULL UNIQUE,
    bom_id BIGINT NOT NULL,
    planned_qty INT NOT NULL,
    completed_qty INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'planned' COMMENT 'planned/material_preparing/producing/qc/completed/paused/cancelled',
    estimated_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    actual_material_cost DECIMAL(12,2) DEFAULT 0.00,
    actual_labor_hours DECIMAL(12,2) DEFAULT 0.00,
    actual_loss_qty DECIMAL(12,2) DEFAULT 0.00,
    start_date DATE NULL,
    due_date DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_production_bom FOREIGN KEY (bom_id) REFERENCES product_bom(id)
) COMMENT='大货生产管理';

CREATE TABLE IF NOT EXISTS purchase_suggestion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    production_order_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    required_qty DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    stock_qty DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    suggest_purchase_qty DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT 'pending/ordered/ignored/done',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_po FOREIGN KEY (production_order_id) REFERENCES production_order(id),
    CONSTRAINT fk_purchase_material FOREIGN KEY (material_id) REFERENCES material(id)
) COMMENT='采购建议';

INSERT IGNORE INTO material (id, material_code, name, category, unit, standard_cost, stock_qty, safety_stock, supplier, lead_time_days) VALUES
(1, 'MAT-PAPER-300G', '300g艺术纸', 'paper', '张', 0.8200, 800, 200, '纸材供应商A', 2),
(2, 'MAT-INK-COLOR', '环保四色油墨', 'ink', '份', 0.1800, 1000, 300, '印刷耗材B', 3),
(3, 'MAT-BAG-CANVAS', '加厚帆布袋胚', 'base_material', '个', 18.5000, 120, 50, '包材工厂C', 7),
(4, 'MAT-PACK-GIFT', '牛皮纸礼盒包装', 'package', '个', 3.2000, 260, 80, '包装供应商D', 5),
(5, 'MAT-STICKER-PVC', '防水PVC贴纸基材', 'base_material', '张', 0.6500, 600, 150, '贴纸材料E', 4);

INSERT IGNORE INTO craft_process (id, process_code, name, category, unit, standard_cost, standard_hours, loss_rate, description) VALUES
(1, 'PROC-DIGITAL-PRINT', '数码印刷', 'printing', '次', 0.9000, 0.0200, 0.0200, '适合小批量明信片/海报打样'),
(2, 'PROC-LAMINATION-MATTE', '哑膜覆膜', 'coating', '次', 0.4500, 0.0100, 0.0100, '提升质感与耐磨'),
(3, 'PROC-DIE-CUT', '模切', 'cutting', '次', 0.3000, 0.0080, 0.0150, '贴纸/异形卡片'),
(4, 'PROC-CANVAS-PRINT', '帆布热转印', 'printing', '次', 6.5000, 0.0800, 0.0300, '帆布袋图案热转印'),
(5, 'PROC-QC-PACK', '质检包装', 'packing', '次', 0.6000, 0.0150, 0.0000, '质检与单件包装');

INSERT IGNORE INTO product_bom (id, bom_no, sku_id, asset_id, product_name, planned_qty, target_price, status, remark) VALUES
(1, 'BOM-AT-PC-001', 1, 1, '山城晚风明信片套装', 100, 29.90, 'confirmed', '样例BOM：明信片套装');

DELETE FROM bom_material_item WHERE bom_id = 1;
INSERT IGNORE INTO bom_material_item (bom_id, material_id, qty, loss_rate, unit_cost, remark) VALUES
(1, 1, 8.0000, 0.0300, 0.8200, '8张/套'),
(1, 2, 8.0000, 0.0200, 0.1800, '四色印刷'),
(1, 4, 1.0000, 0.0100, 3.2000, '礼盒包装');

DELETE FROM bom_process_item WHERE bom_id = 1;
INSERT IGNORE INTO bom_process_item (bom_id, process_id, qty, unit_cost, standard_hours, remark) VALUES
(1, 1, 8.0000, 0.9000, 0.0200, '单张印刷'),
(1, 2, 8.0000, 0.4500, 0.0100, '单张覆膜'),
(1, 5, 1.0000, 0.6000, 0.0150, '质检包装');

-- COMMERCIAL_SAAS_MVP_V1
-- 第一版商业闭环：客户询盘 → AI报价 → 报价单 → BOM/打样
CREATE TABLE IF NOT EXISTS creative_customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_no VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    contact_name VARCHAR(80),
    phone VARCHAR(40),
    wechat VARCHAR(80),
    company VARCHAR(160),
    industry VARCHAR(80),
    source VARCHAR(80),
    notes VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='商业MVP客户表';

CREATE TABLE IF NOT EXISTS customer_inquiry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inquiry_no VARCHAR(80) NOT NULL UNIQUE,
    customer_id BIGINT,
    title VARCHAR(180) NOT NULL,
    product_name VARCHAR(180),
    product_type VARCHAR(80),
    quantity INT DEFAULT 100,
    size VARCHAR(120),
    material VARCHAR(160),
    packaging VARCHAR(160),
    destination VARCHAR(120),
    deadline DATE NULL,
    budget VARCHAR(120),
    usage_scenario VARCHAR(300),
    raw_requirement TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'new' COMMENT 'new/quoted/bom/sample/production/won/lost',
    ai_parse_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_inquiry_customer FOREIGN KEY (customer_id) REFERENCES creative_customer(id)
) COMMENT='客户询盘表';

CREATE TABLE IF NOT EXISTS inquiry_quote (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_no VARCHAR(80) NOT NULL UNIQUE,
    inquiry_id BIGINT NOT NULL,
    customer_id BIGINT,
    product_name VARCHAR(180),
    product_type VARCHAR(80),
    quantity INT NOT NULL DEFAULT 100,
    unit_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    suggested_unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    floor_unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_quote DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    gross_margin_rate DECIMAL(6,4) NOT NULL DEFAULT 0.0000,
    lead_time VARCHAR(200),
    customer_reply TEXT,
    ai_draft TEXT,
    cost_breakdown_json JSON NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT 'draft/sent/accepted/rejected/expired',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_quote_inquiry FOREIGN KEY (inquiry_id) REFERENCES customer_inquiry(id) ON DELETE CASCADE,
    CONSTRAINT fk_quote_customer FOREIGN KEY (customer_id) REFERENCES creative_customer(id)
) COMMENT='询盘报价单';

CREATE TABLE IF NOT EXISTS inquiry_bom_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inquiry_id BIGINT NOT NULL,
    quote_id BIGINT,
    bom_id BIGINT,
    sample_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inquiry_bom_inquiry FOREIGN KEY (inquiry_id) REFERENCES customer_inquiry(id) ON DELETE CASCADE,
    CONSTRAINT fk_inquiry_bom_quote FOREIGN KEY (quote_id) REFERENCES inquiry_quote(id) ON DELETE SET NULL,
    CONSTRAINT fk_inquiry_bom_bom FOREIGN KEY (bom_id) REFERENCES product_bom(id) ON DELETE SET NULL,
    CONSTRAINT fk_inquiry_bom_sample FOREIGN KEY (sample_id) REFERENCES sample_order(id) ON DELETE SET NULL
) COMMENT='询盘到BOM/打样联动';

INSERT IGNORE INTO creative_customer (id, customer_no, name, contact_name, phone, wechat, company, industry, source, notes) VALUES
(1, 'CUS-DEMO-001', '山城文旅客户', '李经理', '13800008888', 'wx-demo', '山城文旅集团', '景区文创', '线下拜访', '样例客户：适合演示询盘、AI报价、BOM和打样闭环');

INSERT IGNORE INTO customer_inquiry (id, inquiry_no, customer_id, title, product_name, product_type, quantity, size, material, packaging, destination, budget, usage_scenario, raw_requirement, status) VALUES
(1, 'INQ-DEMO-001', 1, '山城街巷亚克力钥匙扣定制', '山城街巷钥匙扣', '亚克力钥匙扣', 1000, '5cm，双面印刷，异形', '3mm透明亚克力+金属扣', '独立OPP袋', '上海', '单价15元以内', '景区伴手礼/活动赠品', '客户想做1000个山城街巷主题亚克力钥匙扣，5cm左右，双面彩印，独立包装，发上海，需要报价和交期。', 'new');

-- STAGE2_STAGE3_SCALE_UP_V1
-- 第二阶段：设计到生产项目工作流；第三阶段：SaaS多租户/套餐/用量/模板市场最小闭环
CREATE TABLE IF NOT EXISTS saas_tenant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_no VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    industry VARCHAR(80),
    contact_name VARCHAR(80),
    phone VARCHAR(40),
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='SaaS租户/商家';

CREATE TABLE IF NOT EXISTS saas_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    monthly_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    user_limit INT NOT NULL DEFAULT 3,
    inquiry_limit INT NOT NULL DEFAULT 100,
    ai_quota INT NOT NULL DEFAULT 500,
    project_limit INT NOT NULL DEFAULT 20,
    features_json JSON NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='SaaS套餐';

CREATE TABLE IF NOT EXISTS saas_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'trial' COMMENT 'trial/active/past_due/cancelled',
    start_date DATE NOT NULL,
    end_date DATE NULL,
    auto_renew TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_tenant FOREIGN KEY (tenant_id) REFERENCES saas_tenant(id),
    CONSTRAINT fk_sub_plan FOREIGN KEY (plan_id) REFERENCES saas_plan(id)
) COMMENT='租户订阅';

CREATE TABLE IF NOT EXISTS saas_usage_meter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    usage_month VARCHAR(7) NOT NULL,
    metric VARCHAR(50) NOT NULL COMMENT 'ai_call/inquiry/quote/project/template_use',
    used_count INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_usage (tenant_id, usage_month, metric),
    CONSTRAINT fk_usage_tenant FOREIGN KEY (tenant_id) REFERENCES saas_tenant(id)
) COMMENT='SaaS用量统计';

CREATE TABLE IF NOT EXISTS template_market_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_no VARCHAR(80) NOT NULL UNIQUE,
    tenant_id BIGINT NULL COMMENT 'NULL为平台模板，非NULL为租户私有模板',
    title VARCHAR(160) NOT NULL,
    category VARCHAR(80) NOT NULL COMMENT 'quote/design/bom/marketing/review',
    product_type VARCHAR(80),
    description VARCHAR(500),
    template_json JSON NULL,
    price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    usage_count INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'published',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_template_tenant FOREIGN KEY (tenant_id) REFERENCES saas_tenant(id)
) COMMENT='模板市场';

CREATE TABLE IF NOT EXISTS creative_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_no VARCHAR(80) NOT NULL UNIQUE,
    tenant_id BIGINT NULL,
    inquiry_id BIGINT NULL,
    quote_id BIGINT NULL,
    name VARCHAR(180) NOT NULL,
    theme VARCHAR(300),
    target_audience VARCHAR(300),
    product_types VARCHAR(300),
    budget VARCHAR(120),
    status VARCHAR(40) NOT NULL DEFAULT 'planning' COMMENT 'planning/design/review/bom/sample/production/completed',
    ai_plan TEXT,
    ai_review TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_tenant FOREIGN KEY (tenant_id) REFERENCES saas_tenant(id),
    CONSTRAINT fk_project_inquiry FOREIGN KEY (inquiry_id) REFERENCES customer_inquiry(id) ON DELETE SET NULL,
    CONSTRAINT fk_project_quote FOREIGN KEY (quote_id) REFERENCES inquiry_quote(id) ON DELETE SET NULL
) COMMENT='设计到生产项目';

CREATE TABLE IF NOT EXISTS creative_project_sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    sku_name VARCHAR(180) NOT NULL,
    product_type VARCHAR(80),
    target_price DECIMAL(10,2) DEFAULT 0.00,
    planned_qty INT DEFAULT 100,
    material VARCHAR(160),
    selling_point VARCHAR(500),
    bom_id BIGINT NULL,
    sample_id BIGINT NULL,
    production_order_id BIGINT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'planned',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_sku_project FOREIGN KEY (project_id) REFERENCES creative_project(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_sku_bom FOREIGN KEY (bom_id) REFERENCES product_bom(id) ON DELETE SET NULL,
    CONSTRAINT fk_project_sku_sample FOREIGN KEY (sample_id) REFERENCES sample_order(id) ON DELETE SET NULL,
    CONSTRAINT fk_project_sku_po FOREIGN KEY (production_order_id) REFERENCES production_order(id) ON DELETE SET NULL
) COMMENT='项目推荐SKU与生产联动';

INSERT IGNORE INTO saas_plan (id, plan_code, name, monthly_price, user_limit, inquiry_limit, ai_quota, project_limit, features_json) VALUES
(1, 'STARTER', '报价助手版', 199.00, 3, 100, 500, 10, JSON_OBJECT('quote', true, 'inquiry', true, 'templates', true)),
(2, 'PRO', '专业经营版', 699.00, 10, 800, 3000, 80, JSON_OBJECT('quote', true, 'inquiry', true, 'bom', true, 'sample', true, 'analytics', true)),
(3, 'MANUFACTURE', 'AI文创智造版', 1999.00, 30, 3000, 12000, 300, JSON_OBJECT('quote', true, 'design_to_production', true, 'review', true, 'workflow', true, 'private_templates', true));

INSERT IGNORE INTO saas_tenant (id, tenant_no, name, industry, contact_name, phone) VALUES
(1, 'TNT-DEMO-001', '之间味道自营店', '文创定制', '运营负责人', '13800000001');

INSERT IGNORE INTO saas_subscription (id, tenant_id, plan_id, status, start_date, end_date) VALUES
(1, 1, 3, 'active', CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 1 YEAR));

INSERT IGNORE INTO template_market_item (id, template_no, tenant_id, title, category, product_type, description, template_json, price, usage_count) VALUES
(1, 'TPL-QUOTE-KEYCHAIN', NULL, '亚克力钥匙扣快速报价模板', 'quote', '亚克力钥匙扣', '适合钥匙扣、挂件、异形亚克力快速报价。', JSON_OBJECT('material','3mm透明亚克力','packaging','独立OPP袋','margin',45), 0.00, 0),
(2, 'TPL-DESIGN-CITY', NULL, '城市文创系列企划模板', 'design', '城市礼物', '围绕城市地标、街巷、食物、方言做系列SKU。', JSON_OBJECT('sku','冰箱贴,钥匙扣,明信片,贴纸包','style','温暖地域文化插画'), 0.00, 0),
(3, 'TPL-BOM-POSTCARD', NULL, '明信片套装BOM模板', 'bom', '明信片套装', '8张/套明信片含印刷、覆膜、礼盒包装。', JSON_OBJECT('materials','300g艺术纸,四色油墨,牛皮纸礼盒','process','数码印刷,覆膜,质检包装'), 0.00, 0);

INSERT IGNORE INTO creative_project (id, project_no, tenant_id, inquiry_id, quote_id, name, theme, target_audience, product_types, budget, status) VALUES
(1, 'PRJ-DEMO-001', 1, 1, NULL, '山城街巷文创开发项目', '山城街巷与城市味道', '年轻游客、景区伴手礼客户、企业活动客户', '钥匙扣,冰箱贴,明信片,贴纸包', '29-99元', 'planning');

-- LOGISTICS_TRACKING_MVP_V1
-- 物流跟踪第一版：发货单录入、轨迹同步、异常提醒。后续可接快递100/快递鸟订阅推送。
CREATE TABLE IF NOT EXISTS logistics_carrier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'mock' COMMENT 'mock/kuaidi100/kdniao/sf/manual',
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='物流承运商配置';

CREATE TABLE IF NOT EXISTS logistics_shipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_no VARCHAR(80) NOT NULL UNIQUE,
    order_id BIGINT NULL,
    order_no VARCHAR(80),
    receiver_name VARCHAR(80),
    receiver_phone VARCHAR(40),
    receiver_address VARCHAR(500),
    carrier_code VARCHAR(50) NOT NULL,
    carrier_name VARCHAR(80) NOT NULL,
    tracking_no VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'shipped' COMMENT 'shipped/in_transit/delivering/signed/exception',
    latest_trace VARCHAR(1000),
    alert_level VARCHAR(30) NOT NULL DEFAULT 'normal' COMMENT 'normal/warning/exception',
    shipped_at DATETIME NULL,
    signed_at DATETIME NULL,
    estimated_arrival DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_logistics_tracking_no (tracking_no),
    KEY idx_logistics_order (order_id),
    CONSTRAINT fk_logistics_order FOREIGN KEY (order_id) REFERENCES `order`(id) ON DELETE SET NULL
) COMMENT='物流发货单';

CREATE TABLE IF NOT EXISTS logistics_trace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_id BIGINT NOT NULL,
    trace_time DATETIME NOT NULL,
    status VARCHAR(30) NOT NULL,
    location VARCHAR(160),
    content VARCHAR(1000) NOT NULL,
    raw_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_trace_shipment_time (shipment_id, trace_time),
    CONSTRAINT fk_trace_shipment FOREIGN KEY (shipment_id) REFERENCES logistics_shipment(id) ON DELETE CASCADE
) COMMENT='物流轨迹时间线';

CREATE TABLE IF NOT EXISTS logistics_callback_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(50),
    tracking_no VARCHAR(120),
    payload_json JSON NULL,
    handled TINYINT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='第三方物流推送回调日志';

INSERT IGNORE INTO logistics_carrier (id, code, name, provider, sort_order) VALUES
(1, 'SF', '顺丰速运', 'mock', 1),
(2, 'ZTO', '中通快递', 'mock', 2),
(3, 'YTO', '圆通速递', 'mock', 3),
(4, 'STO', '申通快递', 'mock', 4),
(5, 'YD', '韵达快递', 'mock', 5),
(6, 'JD', '京东物流', 'mock', 6),
(7, 'DBL', '德邦快递', 'mock', 7);

-- LOGISTICS_REAL_API_FIX_V2
-- 禁用演示承运商编码，改为快递100真实公司编码。
UPDATE logistics_carrier SET provider='kuaidi100';
UPDATE logistics_carrier SET code='shunfeng', name='顺丰速运' WHERE code='SF';
UPDATE logistics_carrier SET code='zhongtong', name='中通快递' WHERE code='ZTO';
UPDATE logistics_carrier SET code='yuantong', name='圆通速递' WHERE code='YTO';
UPDATE logistics_carrier SET code='shentong', name='申通快递' WHERE code='STO';
UPDATE logistics_carrier SET code='yunda', name='韵达快递' WHERE code='YD';
UPDATE logistics_carrier SET code='jd', name='京东物流' WHERE code='JD';
UPDATE logistics_carrier SET code='debangwuliu', name='德邦快递' WHERE code='DBL';
INSERT IGNORE INTO logistics_carrier (code, name, provider, sort_order) VALUES
('ems', 'EMS', 'kuaidi100', 8),
('youzhengguonei', '邮政快递包裹', 'kuaidi100', 9),
('jtexpress', '极兔速递', 'kuaidi100', 10),
('annengwuliu', '安能物流', 'kuaidi100', 11),
('kuayue', '跨越速运', 'kuaidi100', 12);

-- INTELLIGENT_WAREHOUSE_MVP_V1
-- 智能仓储：入库、库存、拣货、出库、智能预警
CREATE TABLE IF NOT EXISTS warehouse_location (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    location_code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    zone VARCHAR(40),
    capacity DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='仓库库位';

CREATE TABLE IF NOT EXISTS warehouse_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_type VARCHAR(30) NOT NULL DEFAULT 'SKU' COMMENT 'SKU/MATERIAL/PACKAGE',
    item_id BIGINT NULL,
    item_code VARCHAR(100) NOT NULL,
    item_name VARCHAR(180) NOT NULL,
    spec VARCHAR(180),
    unit VARCHAR(30) NOT NULL DEFAULT '件',
    location_id BIGINT NOT NULL,
    stock_qty DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    locked_qty DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    available_qty DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    safety_stock DECIMAL(12,2) NOT NULL DEFAULT 20.00,
    max_stock DECIMAL(12,2) NOT NULL DEFAULT 9999.00,
    last_in_at DATETIME NULL,
    last_out_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wh_item_loc (item_code, location_id),
    CONSTRAINT fk_wh_inv_location FOREIGN KEY (location_id) REFERENCES warehouse_location(id)
) COMMENT='仓储库存';

CREATE TABLE IF NOT EXISTS warehouse_inbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inbound_no VARCHAR(80) NOT NULL UNIQUE,
    source_type VARCHAR(50) NOT NULL DEFAULT 'purchase' COMMENT 'purchase/production/return/manual',
    supplier VARCHAR(160),
    operator VARCHAR(80),
    remark VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'done',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='入库单';

CREATE TABLE IF NOT EXISTS warehouse_inbound_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inbound_id BIGINT NOT NULL,
    inventory_id BIGINT NOT NULL,
    item_name VARCHAR(180) NOT NULL,
    qty DECIMAL(12,2) NOT NULL,
    unit_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    location_code VARCHAR(80),
    CONSTRAINT fk_wh_in_item_in FOREIGN KEY (inbound_id) REFERENCES warehouse_inbound(id) ON DELETE CASCADE,
    CONSTRAINT fk_wh_in_item_inv FOREIGN KEY (inventory_id) REFERENCES warehouse_inventory(id)
) COMMENT='入库明细';

CREATE TABLE IF NOT EXISTS warehouse_outbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    outbound_no VARCHAR(80) NOT NULL UNIQUE,
    order_no VARCHAR(80),
    purpose VARCHAR(100) NOT NULL DEFAULT '订单发货',
    receiver VARCHAR(120),
    operator VARCHAR(80),
    status VARCHAR(30) NOT NULL DEFAULT 'picking' COMMENT 'picking/shipped/cancelled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='出库单';

CREATE TABLE IF NOT EXISTS warehouse_outbound_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    outbound_id BIGINT NOT NULL,
    inventory_id BIGINT NOT NULL,
    item_name VARCHAR(180) NOT NULL,
    qty DECIMAL(12,2) NOT NULL,
    location_code VARCHAR(80),
    CONSTRAINT fk_wh_out_item_out FOREIGN KEY (outbound_id) REFERENCES warehouse_outbound(id) ON DELETE CASCADE,
    CONSTRAINT fk_wh_out_item_inv FOREIGN KEY (inventory_id) REFERENCES warehouse_inventory(id)
) COMMENT='出库明细';

CREATE TABLE IF NOT EXISTS warehouse_pick_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pick_no VARCHAR(80) NOT NULL UNIQUE,
    outbound_id BIGINT NOT NULL,
    inventory_id BIGINT NOT NULL,
    item_name VARCHAR(180) NOT NULL,
    qty DECIMAL(12,2) NOT NULL,
    location_code VARCHAR(80),
    status VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT 'pending/picking/done/cancelled',
    operator VARCHAR(80),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    CONSTRAINT fk_pick_out FOREIGN KEY (outbound_id) REFERENCES warehouse_outbound(id) ON DELETE CASCADE,
    CONSTRAINT fk_pick_inv FOREIGN KEY (inventory_id) REFERENCES warehouse_inventory(id)
) COMMENT='拣货任务';

CREATE TABLE IF NOT EXISTS warehouse_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_no VARCHAR(80) NOT NULL UNIQUE,
    inventory_id BIGINT NOT NULL,
    alert_type VARCHAR(50) NOT NULL COMMENT 'low_stock/out_of_stock/over_stock/stagnant',
    level VARCHAR(30) NOT NULL DEFAULT 'warning' COMMENT 'info/warning/critical',
    message VARCHAR(300) NOT NULL,
    suggestion VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'open' COMMENT 'open/closed',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wh_alert_inv FOREIGN KEY (inventory_id) REFERENCES warehouse_inventory(id)
) COMMENT='智能仓储预警';

INSERT IGNORE INTO warehouse_location (id, location_code, name, zone, capacity) VALUES
(1, 'A-01-01', 'A区一排一层', 'A', 1000),
(2, 'A-01-02', 'A区一排二层', 'A', 1000),
(3, 'B-02-01', 'B区二排一层', 'B', 800),
(4, 'PK-01', '包装暂存区', 'PK', 500);

INSERT IGNORE INTO warehouse_inventory (id, item_type, item_id, item_code, item_name, spec, unit, location_id, stock_qty, locked_qty, available_qty, safety_stock, max_stock, last_in_at) VALUES
(1, 'SKU', 1, 'AT-PC-001', '山城晚风明信片套装', '100mm x 148mm / 8张', '套', 1, 120, 0, 120, 30, 500, NOW()),
(2, 'SKU', 3, 'AT-ST-002', '花间小憩贴纸包', '6枚/包', '包', 2, 25, 0, 25, 30, 500, NOW()),
(3, 'MATERIAL', 1, 'MAT-PAPER-300G', '300g艺术纸', '印刷纸张', '张', 3, 800, 0, 800, 200, 2000, NOW()),
(4, 'PACKAGE', 4, 'MAT-PACK-GIFT', '牛皮纸礼盒包装', '包装盒', '个', 4, 60, 0, 60, 80, 1000, NOW());

-- COMMERCIAL_FLOW_EXTENSION_V2
-- 询盘报价闭环继续向生产、入库、出库打通
ALTER TABLE inquiry_bom_link ADD COLUMN production_order_id BIGINT NULL;
ALTER TABLE inquiry_bom_link ADD COLUMN warehouse_inbound_id BIGINT NULL;
ALTER TABLE inquiry_bom_link ADD COLUMN warehouse_outbound_id BIGINT NULL;

-- UNIFIED_COMMERCIAL_ORDER_FLOW_V3
-- 项目/BOM -> 详细报价 -> 客户确认 -> 打样或大货 -> 发货，统一使用业务订单号。
CREATE TABLE IF NOT EXISTS commercial_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(80) NOT NULL UNIQUE,
    order_type VARCHAR(20) NOT NULL COMMENT 'sample/bulk',
    project_id BIGINT NULL,
    project_sku_id BIGINT NULL,
    bom_id BIGINT NOT NULL,
    quote_id BIGINT NULL,
    sample_id BIGINT NULL,
    production_order_id BIGINT NULL,
    customer_name VARCHAR(160),
    contact_name VARCHAR(80),
    contact_phone VARCHAR(40),
    receiver_address VARCHAR(500),
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'quoted' COMMENT 'quoted/pending_confirm/confirmed/producing/ready_to_ship/shipped/completed/cancelled',
    confirmed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_commercial_order_type_status(order_type,status),
    KEY idx_commercial_order_bom(bom_id),
    CONSTRAINT fk_co_project FOREIGN KEY(project_id) REFERENCES creative_project(id) ON DELETE SET NULL,
    CONSTRAINT fk_co_project_sku FOREIGN KEY(project_sku_id) REFERENCES creative_project_sku(id) ON DELETE SET NULL,
    CONSTRAINT fk_co_bom FOREIGN KEY(bom_id) REFERENCES product_bom(id),
    CONSTRAINT fk_co_quote FOREIGN KEY(quote_id) REFERENCES cost_quote(id) ON DELETE SET NULL,
    CONSTRAINT fk_co_sample FOREIGN KEY(sample_id) REFERENCES sample_order(id) ON DELETE SET NULL,
    CONSTRAINT fk_co_production FOREIGN KEY(production_order_id) REFERENCES production_order(id) ON DELETE SET NULL
) COMMENT='项目制开发统一商业订单';

-- ORDER_PRODUCTION_SNAPSHOT_V4
-- 每张订单固化创建时的人工生产要求、物料和工艺，历史订单不受后续BOM修改影响。
ALTER TABLE commercial_order ADD COLUMN production_requirement TEXT NULL;
ALTER TABLE commercial_order ADD COLUMN bom_snapshot_json JSON NULL;
ALTER TABLE commercial_order ADD COLUMN material_snapshot_json JSON NULL;
ALTER TABLE commercial_order ADD COLUMN process_snapshot_json JSON NULL;


-- TRIPO_3D_INTEGRATION_V5
ALTER TABLE ai_generation_job ADD COLUMN external_task_id VARCHAR(160) NULL;
ALTER TABLE ai_generation_job ADD COLUMN progress INT NOT NULL DEFAULT 0;
CREATE INDEX idx_ai_job_external_task ON ai_generation_job(external_task_id);
