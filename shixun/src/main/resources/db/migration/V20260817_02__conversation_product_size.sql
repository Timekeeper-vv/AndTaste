-- Persist the requested finished-product size separately from image resolution.
-- The field is optional for historical sessions and required only by the new
-- conversational workflow before it enables image generation.
ALTER TABLE creative_conversation_session
    ADD COLUMN product_size VARCHAR(120) NULL AFTER material;
