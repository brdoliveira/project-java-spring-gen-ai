ALTER TABLE review_state_snapshot
    ADD COLUMN IF NOT EXISTS owner_subject VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

UPDATE review_state_snapshot
SET owner_subject = 'legacy-unowned'
WHERE owner_subject IS NULL;

ALTER TABLE review_state_snapshot
    ALTER COLUMN owner_subject SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_review_state_owner ON review_state_snapshot(owner_subject);
