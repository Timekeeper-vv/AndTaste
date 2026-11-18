-- 好友邀新现金奖励活动：邀请现金账户、返佣流水、提现申请、注销记录

CREATE TABLE IF NOT EXISTS consumer_invite_cash_account (
    user_id BIGINT NOT NULL PRIMARY KEY,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '邀请现金收益余额(可提现)',
    total_earned DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计获得返佣',
    total_withdrawn DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计已提现',
    task_count INT NOT NULL DEFAULT 0 COMMENT '累计积分任务完成次数(提现后清零)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='邀请现金收益账户';

CREATE TABLE IF NOT EXISTS consumer_invite_cash_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inviter_id BIGINT NOT NULL COMMENT '邀请人',
    invitee_id BIGINT NOT NULL COMMENT '被邀请人',
    order_no VARCHAR(80) NULL COMMENT '关联订单号',
    order_amount DECIMAL(12,2) NOT NULL COMMENT '订单实付金额',
    commission DECIMAL(12,2) NOT NULL COMMENT '返佣金额=实付1%',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_invite_cash_inviter (inviter_id, created_at),
    INDEX idx_invite_cash_invitee (invitee_id, created_at)
) COMMENT='邀请返佣流水';

CREATE TABLE IF NOT EXISTS consumer_invite_withdrawal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL COMMENT '本次提现金额',
    status VARCHAR(24) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
    reviewed_by VARCHAR(80) NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_invite_withdrawal_user (user_id, created_at)
) COMMENT='邀请现金提现申请';

CREATE TABLE IF NOT EXISTS user_deletion_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '被注销的原用户',
    identifier VARCHAR(255) NOT NULL COMMENT '手机号或微信标识(用于重新注册识别老用户)',
    identifier_type VARCHAR(20) NOT NULL COMMENT 'phone/openid',
    deleted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_deletion_identifier (identifier, deleted_at)
) COMMENT='账户注销记录，用于限制注销后一周内重新注册不计为被邀请';