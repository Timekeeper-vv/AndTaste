-- AI 文创商品化 MVP：商品模板、渠道目录、报价申请、代销申请。
-- 价格/工期/渠道合作关系均不在此脚本中作商业承诺；真实参数由运营审核后填写。

CREATE TABLE IF NOT EXISTS creative_product_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(80) NOT NULL UNIQUE,
    selection_option_id BIGINT NULL,
    product_name VARCHAR(160) NOT NULL,
    product_type VARCHAR(60) NOT NULL,
    material VARCHAR(500) NOT NULL,
    process VARCHAR(800) NOT NULL,
    specification VARCHAR(300) NOT NULL,
    sample_moq INT NOT NULL DEFAULT 1,
    bulk_moq INT NOT NULL DEFAULT 50,
    sample_fee_yuan DECIMAL(10,2) NULL,
    indicative_retail_display VARCHAR(80) NOT NULL DEFAULT '待运营确认',
    sample_lead_time VARCHAR(80) NOT NULL DEFAULT '待确认',
    bulk_lead_time VARCHAR(80) NOT NULL DEFAULT '待确认',
    supply_status VARCHAR(30) NOT NULL DEFAULT 'pending_confirmation' COMMENT 'pending_confirmation/confirmed/suspended',
    fulfillment_mode VARCHAR(30) NOT NULL DEFAULT 'preorder' COMMENT 'preorder/stocked',
    copyright_requirement VARCHAR(300) NOT NULL,
    published TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cpt_public (published, supply_status, sort_order),
    INDEX idx_cpt_option (selection_option_id)
) COMMENT='C端商品化首批产品模板';

CREATE TABLE IF NOT EXISTS channel_directory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    province VARCHAR(80),
    city VARCHAR(80),
    district VARCHAR(80),
    channel_type VARCHAR(40) NOT NULL DEFAULT 'museum' COMMENT 'museum/scenic_spot/cultural_store/other',
    source_type VARCHAR(40) NOT NULL DEFAULT 'curated_directory' COMMENT 'curated_directory/historical_sales/imported',
    cooperation_status VARCHAR(40) NOT NULL DEFAULT 'directory_only' COMMENT 'directory_only/contacted/cooperating/suspended',
    official_url VARCHAR(500),
    notes VARCHAR(500),
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_channel_region (province, city, enabled),
    INDEX idx_channel_status (cooperation_status, enabled)
) COMMENT='文博文旅渠道目录；目录记录不代表已建立合作';

CREATE TABLE IF NOT EXISTS creative_quote_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NULL,
    product_template_id BIGINT NOT NULL,
    request_type VARCHAR(30) NOT NULL DEFAULT 'sample' COMMENT 'sample/bulk/personal',
    quantity INT NOT NULL DEFAULT 1,
    purpose VARCHAR(30) NOT NULL DEFAULT 'personal' COMMENT 'personal/channel_sale/museum_sale',
    note VARCHAR(1200),
    copyright_basis VARCHAR(30) NOT NULL COMMENT 'original/authorized/public_domain',
    copyright_confirmed TINYINT NOT NULL DEFAULT 0,
    copyright_statement_version VARCHAR(30) NOT NULL DEFAULT 'commercial-v1',
    status VARCHAR(30) NOT NULL DEFAULT 'new' COMMENT 'new/processing/quoted/accepted/rejected/closed',
    quoted_unit_price DECIMAL(10,2) NULL,
    quoted_total_price DECIMAL(12,2) NULL,
    quoted_lead_time VARCHAR(120),
    operator_comment VARCHAR(1200),
    reviewed_by VARCHAR(80),
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cqr_user (user_id, created_at),
    INDEX idx_cqr_status (status, created_at),
    INDEX idx_cqr_asset (asset_id)
) COMMENT='C端商品化报价/打样申请';

CREATE TABLE IF NOT EXISTS creative_consignment_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    product_template_id BIGINT NOT NULL,
    channel_id BIGINT NULL,
    channel_name_snapshot VARCHAR(200),
    sales_mode VARCHAR(30) NOT NULL DEFAULT 'preorder' COMMENT 'preorder/creator_stock/platform_stock',
    creator_share_percent DECIMAL(5,2) NOT NULL DEFAULT 70.00,
    platform_service_percent DECIMAL(5,2) NOT NULL DEFAULT 30.00,
    settlement_basis VARCHAR(300) NOT NULL DEFAULT '按确认销售收入扣除退款、税费及直接履约费用后的可结算金额计算，正式以协议为准',
    inventory_responsibility VARCHAR(100) NOT NULL DEFAULT '创作者或供应商按单生产，平台试运行阶段不承诺备货',
    settlement_cycle VARCHAR(80) NOT NULL DEFAULT '月度对账，售后期结束后结算',
    exclusive_authorization TINYINT NOT NULL DEFAULT 0,
    note VARCHAR(1200),
    copyright_basis VARCHAR(30) NOT NULL COMMENT 'original/authorized/public_domain',
    copyright_confirmed TINYINT NOT NULL DEFAULT 0,
    copyright_statement_version VARCHAR(30) NOT NULL DEFAULT 'commercial-v1',
    authorization_note VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'pending_review' COMMENT 'pending_review/need_materials/approved/rejected/withdrawn',
    operator_comment VARCHAR(1200),
    reviewed_by VARCHAR(80),
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cca_user (user_id, created_at),
    INDEX idx_cca_status (status, created_at),
    INDEX idx_cca_channel (channel_id),
    INDEX idx_cca_asset (asset_id)
) COMMENT='C端作品渠道代销申请';

CREATE TABLE IF NOT EXISTS commercial_application_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_type VARCHAR(30) NOT NULL COMMENT 'quote/consignment',
    application_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    operator VARCHAR(80) NOT NULL,
    comment VARCHAR(1200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_caal_application (application_type, application_id, created_at)
) COMMENT='商品化申请审核留痕';

INSERT IGNORE INTO creative_product_template
    (template_code, selection_option_id, product_name, product_type, material, process, specification,
     sample_moq, bulk_moq, indicative_retail_display, sample_lead_time, bulk_lead_time,
     supply_status, fulfillment_mode, copyright_requirement, published, sort_order)
SELECT 'alloy-magnet', id, '合金冰箱贴', 'alloy_magnet', material, process, specification,
       1, 50, '参考 35-50 元，待运营确认', sample_lead_time, bulk_lead_time,
       'pending_confirmation', 'preorder', '文物、馆藏、地标或 IP 形象需具备原创权利或有效授权；不得直接使用未获授权的馆方标识。', 1, 10
FROM selection_option WHERE option_key='souvenir-alloy-magnet';
INSERT IGNORE INTO creative_product_template
    (template_code, selection_option_id, product_name, product_type, material, process, specification,
     sample_moq, bulk_moq, indicative_retail_display, sample_lead_time, bulk_lead_time,
     supply_status, fulfillment_mode, copyright_requirement, published, sort_order)
SELECT 'zinc-badge', id, '锌合金徽章', 'zinc_badge', material, process, specification,
       1, 50, '参考 30-40 元，待运营确认', sample_lead_time, bulk_lead_time,
       'pending_confirmation', 'preorder', '系列角色、纹样和馆藏元素需完成版权核验；申请代销时必须提交原创或授权依据。', 1, 20
FROM selection_option WHERE option_key='souvenir-zinc-badge';
INSERT IGNORE INTO creative_product_template
    (template_code, selection_option_id, product_name, product_type, material, process, specification,
     sample_moq, bulk_moq, indicative_retail_display, sample_lead_time, bulk_lead_time,
     supply_status, fulfillment_mode, copyright_requirement, published, sort_order)
SELECT 'alloy-keychain', id, '合金钥匙扣', 'alloy_keychain', material, process, specification,
       1, 50, '参考 35-60 元，待运营确认', sample_lead_time, bulk_lead_time,
       'pending_confirmation', 'preorder', '涉及博物馆、景区、企业或他人 IP 时，必须先取得书面授权；平台审核不等于权利授予。', 1, 30
FROM selection_option WHERE option_key='souvenir-alloy-keychain';
INSERT IGNORE INTO creative_product_template
    (template_code, selection_option_id, product_name, product_type, material, process, specification,
     sample_moq, bulk_moq, indicative_retail_display, sample_lead_time, bulk_lead_time,
     supply_status, fulfillment_mode, copyright_requirement, published, sort_order)
SELECT 'canvas-bag', id, '帆布袋', 'canvas_bag', material, process, specification,
       1, 50, '参考 99-149 元，待运营确认', sample_lead_time, bulk_lead_time,
       'pending_confirmation', 'preorder', '图案、字体、人物和品牌标志必须由申请人拥有权利或取得可商业使用授权。', 1, 40
FROM selection_option WHERE option_key='apparel-canvas-bag';
INSERT IGNORE INTO creative_product_template
    (template_code, selection_option_id, product_name, product_type, material, process, specification,
     sample_moq, bulk_moq, indicative_retail_display, sample_lead_time, bulk_lead_time,
     supply_status, fulfillment_mode, copyright_requirement, published, sort_order)
SELECT 'ceramic-mug', id, '陶瓷马克杯', 'ceramic_mug', material, process, specification,
       1, 50, '参考 35-80 元，待运营确认', sample_lead_time, bulk_lead_time,
       'pending_confirmation', 'preorder', '图案及文字必须完成权利核验；食品接触、杯型和耐久性以正式打样与质检结果为准。', 1, 50
FROM selection_option WHERE option_key='tableware-ceramic-mug';

-- 可核验的首批目录记录：这些是公开目录/历史项目线索，不代表平台已与机构签约。
INSERT IGNORE INTO channel_directory (channel_code,name,province,city,district,channel_type,source_type,cooperation_status,notes) VALUES
('museum-palace','故宫博物院','北京市','北京市','东城区','museum','curated_directory','directory_only','仅作为公开渠道目录，未经官方确认不得宣称合作或使用馆方 IP。'),
('museum-national','中国国家博物馆','北京市','北京市','东城区','museum','curated_directory','directory_only','仅作为公开渠道目录，需另行联系采购/文创部门。'),
('museum-capital','首都博物馆','北京市','北京市','西城区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-shanghai','上海博物馆','上海市','上海市','黄浦区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-nanjing','南京博物院','江苏省','南京市','玄武区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-suzhou','苏州博物馆','江苏省','苏州市','姑苏区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-zhejiang','浙江省博物馆','浙江省','杭州市','西湖区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-shaanxi-history','陕西历史博物馆','陕西省','西安市','雁塔区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-qinshihuang','秦始皇帝陵博物院','陕西省','西安市','临潼区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-hunan','湖南博物院','湖南省','长沙市','开福区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-hubei','湖北省博物馆','湖北省','武汉市','武昌区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-guangdong','广东省博物馆','广东省','广州市','天河区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-sichuan','四川博物院','四川省','成都市','青羊区','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。'),
('museum-sanxingdui','三星堆博物馆','四川省','德阳市','广汉市','museum','curated_directory','directory_only','仅作为公开渠道目录，合作状态待运营核实。');

-- 将历史销售表中已出现的文博/美术馆项目纳入可搜索目录，标记为历史线索而非合作渠道。
INSERT IGNORE INTO channel_directory (channel_code,name,channel_type,source_type,cooperation_status,notes)
SELECT CONCAT('history-', MD5(TRIM(project_name))), TRIM(project_name), 'museum', 'historical_sales', 'directory_only',
       '名称来自历史销售数据项目字段，仅表示历史业务线索，不代表当前合作、采购意向或官方授权。'
FROM historical_sales_fact
WHERE TRIM(project_name) <> ''
  AND (project_name LIKE '%博物馆%' OR project_name LIKE '%博物院%' OR project_name LIKE '%美术馆%');
