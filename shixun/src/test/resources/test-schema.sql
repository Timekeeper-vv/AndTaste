CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    age INT,
    email VARCHAR(200),
    phone VARCHAR(30),
    password VARCHAR(255) NOT NULL,
    -- Mapper-level unit tests intentionally omit role; production registration
    -- always supplies it and the production schema keeps it NOT NULL.
    role VARCHAR(20) DEFAULT 'user',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active'
);

CREATE TABLE brand_style_profile (
    id BIGINT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    base_prompt VARCHAR(2000),
    negative_prompt VARCHAR(2000),
    palette VARCHAR(500),
    cultural_guardrails VARCHAR(2000),
    enabled TINYINT NOT NULL DEFAULT 1
);

INSERT INTO brand_style_profile (
    id, name, base_prompt, negative_prompt, cultural_guardrails, enabled
) VALUES (
    1, '测试风格', 'premium cultural creative product', 'low quality, watermark',
    'use only original and authorized cultural elements', 1
);

CREATE TABLE digital_asset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_no VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    asset_type VARCHAR(30) NOT NULL,
    source_type VARCHAR(30),
    file_url VARCHAR(1000),
    preview_url VARCHAR(1000),
    prompt CLOB,
    negative_prompt CLOB,
    style_id BIGINT,
    version_no INT NOT NULL DEFAULT 1,
    parent_asset_id BIGINT,
    format VARCHAR(30),
    tags VARCHAR(1000),
    metadata_json CLOB,
    status VARCHAR(30),
    created_by BIGINT,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

CREATE TABLE creative_multiview_bundle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bundle_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    input_asset_id BIGINT,
    product_key VARCHAR(120),
    product_name VARCHAR(180),
    material VARCHAR(180),
    product_size VARCHAR(120),
    view_count INT NOT NULL DEFAULT 3,
    status VARCHAR(30) NOT NULL DEFAULT 'draft',
    purpose VARCHAR(30),
    museum_id VARCHAR(80),
    museum_name VARCHAR(200),
    campaign_key VARCHAR(100),
    note VARCHAR(1200),
    review_comment VARCHAR(1200),
    reviewed_by VARCHAR(80),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE creative_multiview_bundle_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bundle_id BIGINT NOT NULL,
    view_key VARCHAR(20) NOT NULL,
    asset_id BIGINT NOT NULL,
    label VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE channel_directory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    province VARCHAR(80),
    city VARCHAR(80),
    district VARCHAR(80),
    channel_type VARCHAR(30) NOT NULL,
    source_type VARCHAR(50),
    cooperation_status VARCHAR(50),
    notes VARCHAR(1000),
    enabled TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE consumer_campaign_reward (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    participation_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    campaign_key VARCHAR(80) NOT NULL,
    asset_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'pending_review',
    reward_amount DECIMAL(12,2) NOT NULL,
    credit_transaction_id BIGINT NULL,
    reviewed_by VARCHAR(80),
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, campaign_key),
    UNIQUE (asset_id)
);

CREATE TABLE ai_generation_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_no VARCHAR(80) NOT NULL UNIQUE,
    job_type VARCHAR(30) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(120),
    style_id BIGINT,
    input_asset_id BIGINT,
    output_asset_id BIGINT,
    product_key VARCHAR(80),
    product_name VARCHAR(160),
    product_material VARCHAR(500),
    prompt CLOB,
    negative_prompt CLOB,
    status VARCHAR(30) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    error_message CLOB,
    export_formats VARCHAR(120),
    request_payload_json JSON,
    result_payload_json JSON,
    external_task_id VARCHAR(160),
    created_by BIGINT,
    credit_transaction_id BIGINT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

CREATE TABLE consumer_credit_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_recharged DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_consumed DECIMAL(12,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE consumer_credit_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    job_id BIGINT,
    asset_id BIGINT,
    biz_type VARCHAR(50) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    direction VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    balance_before DECIMAL(12,2),
    balance_after DECIMAL(12,2),
    remark VARCHAR(2000),
    operator VARCHAR(100),
    created_at TIMESTAMP NULL
);
