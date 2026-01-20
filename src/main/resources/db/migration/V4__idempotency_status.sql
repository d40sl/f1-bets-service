-- Add status column to track in-progress vs completed requests
ALTER TABLE idempotency_keys 
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';

ALTER TABLE idempotency_keys
    ADD CONSTRAINT status_valid CHECK (status IN ('IN_PROGRESS', 'COMPLETED'));

-- Make response fields nullable for in-progress state
ALTER TABLE idempotency_keys 
    ALTER COLUMN response_payload DROP NOT NULL;

ALTER TABLE idempotency_keys 
    ALTER COLUMN response_status DROP NOT NULL;

-- Index for finding in-progress requests that may be stale
CREATE INDEX idx_idempotency_status ON idempotency_keys(status, created_at);
