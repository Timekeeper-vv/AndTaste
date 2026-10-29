CREATE TABLE IF NOT EXISTS consumer_points_invite_code (
    user_id BIGINT NOT NULL PRIMARY KEY,
    invite_code VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_points_invite_code (invite_code)
) COMMENT='C端用户邀请码';

CREATE TABLE IF NOT EXISTS consumer_points_invite_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inviter_id BIGINT NOT NULL,
    invitee_id BIGINT NOT NULL,
    invite_code VARCHAR(16) NOT NULL,
    inviter_points INT NOT NULL DEFAULT 80,
    invitee_points INT NOT NULL DEFAULT 200,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_points_invite_invitee (invitee_id),
    INDEX idx_points_invite_inviter (inviter_id, created_at)
) COMMENT='邀请拉新记录，每个新用户只可被邀请一次';

CREATE TABLE IF NOT EXISTS consumer_points_usage_daily (
    user_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,
    active_seconds INT NOT NULL DEFAULT 0,
    awarded_points INT NOT NULL DEFAULT 0,
    last_heartbeat_at DATETIME NULL,
    PRIMARY KEY (user_id, stat_date)
) COMMENT='有效使用时长累计与积分发放';

CREATE TABLE IF NOT EXISTS consumer_points_share_claim (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    claim_date DATE NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'timeline',
    points INT NOT NULL DEFAULT 80,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_points_share_day (user_id, claim_date)
) COMMENT='分享朋友圈积分领取，每日一次';
