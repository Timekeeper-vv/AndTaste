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

-- 初始用户数据（密码在服务层经 BCrypt 编码后存储，通过 API 创建的账号可正常登录）
INSERT IGNORE INTO user (username, age, email, phone, password, role) VALUES
('superadmin', 30, 'superadmin@andtaste.com', '13800000001', '123456', 'admin'),
('approver01', 28, 'approver01@andtaste.com', '13800000002', '123456', 'technician'),
('employee01', 24, 'employee01@andtaste.com', '13800000003', '123456', 'feeder');

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
