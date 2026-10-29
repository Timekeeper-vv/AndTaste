CREATE TABLE IF NOT EXISTS consumer_points_checkin (
    user_id BIGINT NOT NULL PRIMARY KEY,
    last_checkin_date DATE NULL,
    streak_days INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='C端每日签到连续天数';

CREATE TABLE IF NOT EXISTS consumer_points_creation_reward (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    reward_kind VARCHAR(24) NOT NULL,
    points INT NOT NULL,
    credit_transaction_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_points_creation_reward_asset (asset_id),
    INDEX idx_points_creation_reward_user (user_id,created_at)
) COMMENT='AI创作积分奖励幂等记录';

CREATE TABLE IF NOT EXISTS consumer_points_red_packet_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_date DATE NOT NULL,
    event_slot VARCHAR(8) NOT NULL,
    packet_limit INT NOT NULL DEFAULT 100,
    points_min INT NOT NULL DEFAULT 2,
    points_max INT NOT NULL DEFAULT 10,
    claimed_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_points_packet_event (event_date, event_slot)
) COMMENT='每日积分红包雨场次';

CREATE TABLE IF NOT EXISTS consumer_points_red_packet_claim (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    points INT NOT NULL,
    claimed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_points_packet_claim (event_id, user_id),
    INDEX idx_points_packet_user (user_id, claimed_at)
) COMMENT='积分红包领取记录';

CREATE TABLE IF NOT EXISTS consumer_points_quiz_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(100) NULL,
    question_text VARCHAR(1000) NOT NULL,
    option_a VARCHAR(500) NOT NULL,
    option_b VARCHAR(500) NOT NULL,
    option_c VARCHAR(500) NOT NULL,
    option_d VARCHAR(500) NOT NULL,
    correct_option CHAR(1) NOT NULL,
    explanation VARCHAR(1000) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_points_question_active (active, category)
) COMMENT='积分活动四选一题库';

CREATE TABLE IF NOT EXISTS consumer_points_quiz_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_date DATE NOT NULL,
    sequence_no INT NOT NULL,
    current_position INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress',
    wrong_retry_after DATETIME NULL,
    credit_transaction_id BIGINT NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_points_quiz_sequence (user_id, session_date, sequence_no),
    INDEX idx_points_quiz_user (user_id, session_date, status)
) COMMENT='积分答题场次';

CREATE TABLE IF NOT EXISTS consumer_points_quiz_session_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    position_no INT NOT NULL,
    question_id BIGINT NOT NULL,
    solved TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uq_points_quiz_question_position (session_id, position_no),
    UNIQUE KEY uq_points_quiz_question (session_id, question_id)
) COMMENT='积分答题场次题目快照';

CREATE TABLE IF NOT EXISTS consumer_points_quiz_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    position_no INT NOT NULL,
    selected_option CHAR(1) NOT NULL,
    is_correct TINYINT(1) NOT NULL,
    answered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_points_quiz_attempt (session_id, position_no, answered_at)
) COMMENT='积分答题作答记录';

CREATE TABLE IF NOT EXISTS consumer_bounty_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_key VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    brief TEXT NOT NULL,
    deadline DATE NULL,
    reward_amount INT NOT NULL DEFAULT 2000,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bounty_task_status_deadline (status,deadline)
) COMMENT='创作悬赏任务';

INSERT IGNORE INTO consumer_bounty_task (campaign_key,title,brief,deadline,reward_amount,status)
VALUES (
    'yunnan_museum_cultural_product_2026',
    '云南省博物馆文创产品征集令',
    '围绕云南省博物馆馆藏文物进行原创文创设计，品类不限。提交作品需填写产品名称、品类、特色功能、文物原型和产品介绍，并上传全色三视图或设计稿。投稿作品一经遴选采用，平台联系原创作者洽谈合作。每个账号最多提交5件作品。',
    NULL,
    2000,
    'draft'
);

CREATE TABLE IF NOT EXISTS consumer_bounty_submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    product_category VARCHAR(100) NOT NULL,
    product_features VARCHAR(1000) NOT NULL,
    artifact_prototype VARCHAR(500) NOT NULL,
    product_description TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'pending_review',
    credit_transaction_id BIGINT NULL,
    selected_for_sample TINYINT(1) NOT NULL DEFAULT 0,
    reviewed_by VARCHAR(80) NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_bounty_task_asset (task_id,asset_id),
    INDEX idx_bounty_user_task (user_id,task_id),
    INDEX idx_bounty_review (status,created_at)
) COMMENT='悬赏任务作品投稿';

CREATE TABLE IF NOT EXISTS consumer_bounty_sample_voucher (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'available',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    redeemed_at DATETIME NULL,
    INDEX idx_bounty_voucher_user (user_id,status)
) COMMENT='悬赏入选作品免费树脂打样凭证';
