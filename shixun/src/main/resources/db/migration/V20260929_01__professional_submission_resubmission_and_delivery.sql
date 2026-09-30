-- Product-making follow-up fields. This migration is additive so existing
-- submissions, quotes, payments and uploaded ZIP files remain untouched.
ALTER TABLE consumer_professional_submission
    ADD COLUMN resubmission_count INT NOT NULL DEFAULT 0 AFTER review_comment,
    ADD COLUMN sample_quantity INT NOT NULL DEFAULT 1 AFTER quoted_sample_note,
    ADD COLUMN recipient_name VARCHAR(120) NULL AFTER sample_quantity,
    ADD COLUMN recipient_phone VARCHAR(40) NULL AFTER recipient_name,
    ADD COLUMN recipient_address VARCHAR(500) NULL AFTER recipient_phone;
