-- 第五阶段：样品物流跟踪与生产异常提醒。
-- 独立于旧版 logistics_shipment，避免改写历史商业订单物流数据；以样品申请为唯一业务主键。

CREATE TABLE IF NOT EXISTS creative_sample_logistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    carrier_code VARCHAR(50) NULL,
    carrier_name VARCHAR(80) NULL,
    tracking_no VARCHAR(120) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT 'pending/shipped/in_transit/delivering/signed/exception/returned',
    latest_trace VARCHAR(1000) NULL,
    alert_level VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT 'normal/warning/exception',
    alert_status VARCHAR(20) NOT NULL DEFAULT 'resolved' COMMENT 'open/acknowledged/resolved',
    exception_note VARCHAR(2000) NULL,
    shipped_at DATETIME NULL,
    signed_at DATETIME NULL,
    estimated_arrival DATETIME NULL,
    last_synced_at DATETIME NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cslog_request (request_id),
    UNIQUE KEY uk_cslog_tracking (tracking_no),
    INDEX idx_cslog_user_updated (user_id, updated_at),
    INDEX idx_cslog_alert (alert_status, alert_level, updated_at)
) COMMENT='C端样品物流当前状态';

CREATE TABLE IF NOT EXISTS creative_sample_logistics_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    logistics_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL COMMENT 'tracking_updated/status_changed/exception_marked/exception_resolved',
    status VARCHAR(30) NULL,
    alert_level VARCHAR(20) NULL,
    location VARCHAR(160) NULL,
    content VARCHAR(1000) NOT NULL,
    payload_json JSON NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_csle_logistics_time (logistics_id, created_at, id),
    INDEX idx_csle_request_time (request_id, created_at, id)
) COMMENT='C端样品物流轨迹和异常审计';
