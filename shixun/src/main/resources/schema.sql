CREATE DATABASE IF NOT EXISTS shixun;
USE shixun;

CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    age INT,
    email VARCHAR(200),
    phone VARCHAR(20),
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'admin' COMMENT 'admin=超级管理员, technician=审批主管, feeder=员工'
);
-- 兼容已存在的数据库：为旧表补加 role 列（列已存在时报错会被 continue-on-error 忽略）
ALTER TABLE `user` ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'admin';

CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price DOUBLE,
    stock INT,
    category VARCHAR(100),
    description VARCHAR(500)
);

-- 圈舍资产表
CREATE TABLE IF NOT EXISTS pens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pen_code VARCHAR(50) NOT NULL UNIQUE COMMENT '圈舍编号',
    pen_name VARCHAR(100) NOT NULL COMMENT '圈舍名称',
    capacity INT NOT NULL DEFAULT 0 COMMENT '设计容量',
    responsible_person VARCHAR(50) COMMENT '责任人',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用,0停用',
    current_count INT NOT NULL DEFAULT 0 COMMENT '当前存栏数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 兽药疫苗标准库
CREATE TABLE IF NOT EXISTS drugs_vaccines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(20) NOT NULL COMMENT 'VACCINE疫苗,DRUG药品',
    generic_name VARCHAR(100) NOT NULL COMMENT '通用名',
    specification VARCHAR(100) COMMENT '规格',
    manufacturer VARCHAR(100) COMMENT '生产厂家',
    description TEXT COMMENT '用途说明',
    image_url MEDIUMTEXT COMMENT '产品图片(base64)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 兼容已存在的数据库：补加列（列已存在时报错被 continue-on-error 忽略）
ALTER TABLE drugs_vaccines ADD COLUMN description TEXT COMMENT '用途说明';
ALTER TABLE drugs_vaccines ADD COLUMN image_url MEDIUMTEXT COMMENT '产品图片(base64)';

-- 养殖批次表
CREATE TABLE IF NOT EXISTS batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_code VARCHAR(50) NOT NULL UNIQUE COMMENT '批次号',
    entry_date DATE NOT NULL COMMENT '入栏日期',
    breed VARCHAR(100) NOT NULL COMMENT '品种',
    source VARCHAR(200) COMMENT '来源地',
    initial_pen_id BIGINT COMMENT '初始圈舍ID',
    notes TEXT COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 个体档案表
CREATE TABLE IF NOT EXISTS animals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ear_tag VARCHAR(50) NOT NULL UNIQUE COMMENT '耳标号(全局唯一)',
    gender VARCHAR(10) NOT NULL COMMENT 'MALE,FEMALE',
    entry_date DATE NOT NULL COMMENT '入栏日期',
    breed VARCHAR(100) NOT NULL COMMENT '品种',
    batch_id BIGINT NOT NULL COMMENT '所属批次',
    current_pen_id BIGINT COMMENT '当前圈舍',
    birth_weight DECIMAL(8,2) COMMENT '出生重量(kg)',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE在栏,SOLD已出栏,DEAD死亡',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 免疫记录表
CREATE TABLE IF NOT EXISTS immunization_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ear_tag VARCHAR(50) NOT NULL COMMENT '耳标号',
    vaccine_id BIGINT NOT NULL COMMENT '疫苗ID',
    event_time DATE NOT NULL COMMENT '免疫日期',
    dosage VARCHAR(50) COMMENT '剂量',
    operator VARCHAR(50) COMMENT '执行人',
    notes TEXT COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用药记录表
CREATE TABLE IF NOT EXISTS medication_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ear_tag VARCHAR(50) NOT NULL COMMENT '耳标号',
    drug_id BIGINT NOT NULL COMMENT '药品ID',
    reason TEXT COMMENT '用药原因',
    event_time DATE NOT NULL COMMENT '用药日期',
    dosage VARCHAR(50) COMMENT '剂量',
    operator VARCHAR(50) COMMENT '执行人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 转舍记录表（事务原子操作保障数据一致性）
CREATE TABLE IF NOT EXISTS pen_transfer_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ear_tag VARCHAR(50) NOT NULL COMMENT '耳标号',
    from_pen_id BIGINT COMMENT '原圈舍ID',
    to_pen_id BIGINT NOT NULL COMMENT '目标圈舍ID',
    event_time DATE NOT NULL COMMENT '转舍日期',
    reason TEXT COMMENT '转舍原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 死亡记录表
CREATE TABLE IF NOT EXISTS death_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ear_tag VARCHAR(50) NOT NULL COMMENT '耳标号',
    event_time DATE NOT NULL COMMENT '死亡日期',
    cause VARCHAR(200) COMMENT '死亡原因',
    operator VARCHAR(50) COMMENT '记录人',
    notes TEXT COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 出栏记录表
CREATE TABLE IF NOT EXISTS slaughter_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ear_tag VARCHAR(50) NOT NULL COMMENT '耳标号',
    event_time DATE NOT NULL COMMENT '出栏日期',
    type VARCHAR(20) NOT NULL COMMENT 'SALE销售,SLAUGHTER屠宰,TRANSFER转移',
    destination VARCHAR(200) COMMENT '目的地',
    weight DECIMAL(8,2) COMMENT '重量(kg)',
    price DECIMAL(10,2) COMMENT '价格(元)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 初始用户数据（密码在服务层经 BCrypt 编码后存储，通过 API 创建的账号可正常登录）
INSERT IGNORE INTO user (username, age, email, phone, password, role) VALUES
('superadmin', 30, 'superadmin@farm.com', '13800000001', '123456', 'admin'),
('approver01', 28, 'approver01@farm.com', '13800000002', '123456', 'technician'),
('employee01', 24, 'employee01@farm.com', '13800000003', '123456', 'feeder'),
('testuser', 20, 'test@example.com', '13800138000', '123456', 'feeder');

INSERT IGNORE INTO product (name, price, stock, category, description) VALUES
('iPhone', 5999.00, 100, 'Electronics', 'Latest smartphone'),
('Sneakers', 299.00, 200, 'Shoes', 'Comfortable sport shoes'),
('Laptop', 4599.00, 50, 'Electronics', 'Lightweight laptop'),
('Coffee Mug', 39.00, 500, 'Household', 'Ceramic mug 350ml');

-- 供应商银行账户表：AI 助手和供应商列表都从这里实时读取，不再依赖前端/AI 写死数据
CREATE TABLE IF NOT EXISTS supplier_bank_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiver_no VARCHAR(40) NOT NULL UNIQUE COMMENT '收方编号',
    supplier VARCHAR(200) NOT NULL COMMENT '供应商名称',
    account_type VARCHAR(50) NOT NULL DEFAULT '对公账户' COMMENT '账户类型',
    account_name VARCHAR(200) NOT NULL COMMENT '供应商户名',
    bank_account VARCHAR(80) NOT NULL COMMENT '银行账号',
    bank VARCHAR(200) NOT NULL COMMENT '银行',
    branch VARCHAR(200) COMMENT '开户行',
    location VARCHAR(100) COMMENT '开户行所在地',
    note VARCHAR(1000) COMMENT '备注/核验提示',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='供应商银行账户信息';

INSERT IGNORE INTO supplier_bank_accounts (receiver_no, supplier, account_type, account_name, bank_account, bank, branch, location, note) VALUES
('2025051500575','深圳市星米三维科技有限公司','对公账户','深圳市星米三维科技有限公司','755958121410901','招商银行','招商银行有限公司深圳布吉支行','深圳市',NULL),
('2024082100473','秦皇岛轩阳贸易有限公司','对公账户','秦皇岛轩阳贸易有限公司','50813001040028701','中国农业银行秦皇岛港城支行','秦皇岛港城支行','河北省秦皇岛',NULL),
('2024081500471','山东珂芮慕斯食品有限公司','对公账户','山东珂芮慕斯食品有限公司','531906954310908','招商银行股份有限公司','济南工业南路支行','山东省济南市',NULL),
('2024080200468','武汉扬子江普啦啦食品有限公司','对公账户','武汉扬子江普啦啦食品有限公司','8111501013401163157','中信银行股份有限公司武汉江夏支行','中信银行股份有限公司','武汉',NULL),
('2024080100465','天津华明乳业有限公司','对公账户','天津华明乳业有限公司','817980001421010192','威海银行股份有限公司','威海银行股份有限公司','天津','原始数据中“银行”字段为“w”，已按上下文推断为威海银行股份有限公司；付款前务必二次核实。'),
('2024062500444','海城市金城果糖厂','对公账户','海城市金城果糖厂','241311950010251030380','交通银行鞍山海城支行','交通银行','辽宁省海城市',NULL),
('2024062500441','浙江冰富生物科技有限公司','对公账户','浙江冰富生物科技有限公司','584715957600015','浙江民泰商业银行股份有限公司湖州练市小微综合支行','浙江民泰商业银行股份有限公司','浙江省湖州市',NULL),
('2024061300434','佛山市恒邦达新材料科技有限公司','对公账户','佛山市恒邦达新材料科技有限公司','2013016809200067848','中国工商银行','中国工商银行股份有限公司佛山南海中海万锦支行','广东佛山',NULL),
('2024061100428','上海方棱轻工机械厂','对公账户','上海方棱轻工机械厂','31001972000055657058','中国建设银行','上海星火支行','上海市',NULL),
('2024060600427','英唯奕(上海)餐饮管理有限公司','对公账户','英唯奕(上海)餐饮管理有限公司','31050136360000001091','中国建设银行股份有限公司','上海市分行','上海市',NULL),
('2024060600426','厚得（广东）生物科技有限公司','对公账户','厚得（广东）生物科技有限公司','3602886609100274693','中国工商银行股份有限公司','广州增城开发区支行','广东省广州市',NULL),
('2024053100420','福州中商贸易有限公司','对公账户','福州中商贸易有限公司','631890227','民生银行','中国民生银行股份有限公司福州广达支行','福建省福州市',NULL),
('2024052800419','秦皇岛鹏泽糖业有限公司','对公账户','秦皇岛鹏泽糖业有限公司','50825001040002892','中国农业银行','昌黎龙家店分理处','河北秦皇岛',NULL),
('2024052000413','东莞市宸宇包装有限公司','对公账户','东莞市宸宇包装有限公司','44050177623800000656','中国建设银行','中国建设银行股份有限公司东莞东宝路支行','广东省东莞市',NULL),
('2024052000412','天津市佳越商贸有限公司','对公账户','天津市佳越商贸有限公司','271360075117','中国银行','中国银行股份有限公司天津北宁支行','天津市',NULL),
('2024052000411','广州恒生包装制品有限公司','对公账户','广州恒生包装制品有限公司','3602864309100122837','中国工商银行','中国工商银行股份有限公司广州晓港支行','广东省广州市',NULL),
('2024052000410','佛山市展智鸿货架有限公司','对公账户','佛山市展智鸿货架有限公司','2013026709200043239','中国工商银行','中国工商银行股份有限公司佛山分行','广东省佛山市',NULL),
('2024052000409','华测检测认证集团北京有限公司','对公账户','华测检测认证集团北京有限公司','999012825710506','招商银行','招商银行北京亦庄支行','北京市朝阳区',NULL),
('2024052000408','广东元宇火光印刷有限公司','对公账户','广东元宇火光印刷有限公司','2004023009200126778','中国工商银行','中国工商银行股份有限公司潮安支行','广东省潮州市',NULL),
('2024052000407','青岛益美鑫包装科技有限公司','对公账户','青岛益美鑫包装科技有限公司','37150198691000004147','中国建设银行','中国建设银行青岛中山路支行','山东省青岛市',NULL);


-- 圈舍样本数据
INSERT IGNORE INTO pens (pen_code, pen_name, capacity, responsible_person, status, current_count) VALUES
('PEN-A', 'A号圈舍', 50, '张三', 1, 2),
('PEN-B', 'B号圈舍', 80, '李四', 1, 1),
('PEN-C', 'C号圈舍', 60, '王五', 1, 0);

-- 兽药疫苗样本数据
INSERT IGNORE INTO drugs_vaccines (category, generic_name, specification, manufacturer) VALUES
('VACCINE', '猪瘟活疫苗', '1头份/瓶', '中农威特生物科技股份有限公司'),
('VACCINE', '口蹄疫O型灭活疫苗', '1mL/头份', '中国农业科学院兰州兽医研究所'),
('DRUG', '阿莫西林颗粒', '10%，100g/袋', '河南牧翔动物药业有限公司'),
('DRUG', '恩诺沙星注射液', '2.5%，100mL/瓶', '齐鲁动物保健品有限公司');

-- 养殖批次样本数据
INSERT IGNORE INTO batches (batch_code, entry_date, breed, source, initial_pen_id, notes) VALUES
('BATCH-2024-001', '2024-01-15', '杜洛克猪', '河南省洛阳市', 1, '首批引进优质种猪，经检疫合格');

-- 个体档案样本数据
INSERT IGNORE INTO animals (ear_tag, gender, entry_date, breed, batch_id, current_pen_id, birth_weight, status) VALUES
('ET-001', 'MALE', '2024-01-15', '杜洛克猪', 1, 1, 25.50, 'ACTIVE'),
('ET-002', 'FEMALE', '2024-01-15', '杜洛克猪', 1, 1, 23.80, 'ACTIVE'),
('ET-003', 'MALE', '2024-01-15', '杜洛克猪', 1, 2, 26.20, 'ACTIVE');

-- 统一申请单 / 审批流
CREATE TABLE IF NOT EXISTS workflow_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_no VARCHAR(50) NOT NULL UNIQUE COMMENT '申请单号',
    category VARCHAR(20) NOT NULL COMMENT 'finance/chain',
    type_key VARCHAR(50) NOT NULL COMMENT '申请类型编码',
    title VARCHAR(200) NOT NULL COMMENT '申请标题',
    applicant VARCHAR(100) NOT NULL COMMENT '申请人',
    applicant_role VARCHAR(20) NOT NULL COMMENT '申请人角色',
    form_data_json JSON NOT NULL COMMENT '申请表单内容',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
    approver VARCHAR(100) DEFAULT NULL COMMENT '审批人',
    approval_comment TEXT COMMENT '审批意见',
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    approved_at DATETIME DEFAULT NULL COMMENT '审批时间',
    rejected_at DATETIME DEFAULT NULL COMMENT '驳回时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除'
) COMMENT='统一申请单';

CREATE TABLE IF NOT EXISTS workflow_approval_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL COMMENT '申请单ID',
    action VARCHAR(20) NOT NULL COMMENT 'submit/approve/reject',
    operator VARCHAR(100) NOT NULL COMMENT '操作人',
    operator_role VARCHAR(20) NOT NULL COMMENT '操作人角色',
    comment TEXT COMMENT '操作意见',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_workflow_log_app_id (application_id)
) COMMENT='审批流日志';
