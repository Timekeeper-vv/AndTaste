-- A channel request is an internal handoff; it does not imply museum receipt or approval.
ALTER TABLE consumer_professional_submission
    ADD COLUMN channel_request_status VARCHAR(24) NOT NULL DEFAULT 'not_requested' AFTER museum_name,
    ADD COLUMN channel_requested_at DATETIME NULL AFTER channel_request_status;
