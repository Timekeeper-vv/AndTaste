-- Museum/channel review is separate from the platform review.  Keep this in
-- a new migration because V20261001_01 may already have been applied.
ALTER TABLE consumer_professional_submission
    ADD COLUMN museum_review_status VARCHAR(24) NOT NULL DEFAULT 'not_started' AFTER channel_requested_at,
    ADD COLUMN museum_review_comment VARCHAR(1000) NULL AFTER museum_review_status,
    ADD COLUMN museum_reviewed_at DATETIME NULL AFTER museum_review_comment;
