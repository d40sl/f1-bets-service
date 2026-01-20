-- Add FAILED status to allow marking requests that threw exceptions
-- This enables retry semantics for failed requests while preventing replay of successful ones

ALTER TABLE idempotency_keys
    DROP CONSTRAINT status_valid;

ALTER TABLE idempotency_keys
    ADD CONSTRAINT status_valid CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'));

-- Index for cleanup of stale IN_PROGRESS entries (stuck requests)
CREATE INDEX idx_idempotency_in_progress ON idempotency_keys(status, created_at) 
    WHERE status = 'IN_PROGRESS';
