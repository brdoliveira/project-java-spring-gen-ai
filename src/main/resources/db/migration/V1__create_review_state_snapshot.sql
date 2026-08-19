CREATE TABLE IF NOT EXISTS review_state_snapshot (
  id                UUID PRIMARY KEY,
  status            VARCHAR(50) NOT NULL,
  checkpoint        VARCHAR(50),
  file_name         VARCHAR(255),
  report_markdown   TEXT,
  error_message     TEXT,
  prompt_snapshot   TEXT,
  prompt_history    JSONB,
  plan_json         JSONB,
  pending_approval  JSONB,
  approval_history  JSONB,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_review_state_status ON review_state_snapshot(status);
CREATE INDEX IF NOT EXISTS idx_review_state_checkpoint ON review_state_snapshot(checkpoint);
CREATE INDEX IF NOT EXISTS idx_review_state_created_at ON review_state_snapshot(created_at);
