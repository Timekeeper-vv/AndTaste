CREATE DATABASE IF NOT EXISTS shixun;
USE shixun;

CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    age INT,
    email VARCHAR(200),
    phone VARCHAR(20),
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'admin'
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE在栏,SOLD已出栏',
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
('testuser', 20, 'test@example.com', '13800138000', '123456', 'admin'),
('admin', 25, 'admin@farm.com', '13800000001', '123456', 'admin'),
('tech01', 28, 'tech01@farm.com', '13800000002', '123456', 'technician'),
('feeder01', 22, 'feeder01@farm.com', '13800000003', '123456', 'feeder');

INSERT IGNORE INTO product (name, price, stock, category, description) VALUES
('iPhone', 5999.00, 100, 'Electronics', 'Latest smartphone'),
('Sneakers', 299.00, 200, 'Shoes', 'Comfortable sport shoes'),
('Laptop', 4599.00, 50, 'Electronics', 'Lightweight laptop'),
('Coffee Mug', 39.00, 500, 'Household', 'Ceramic mug 350ml');

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
