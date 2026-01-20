-- Add idempotency key column to bets table for crash-safe deduplication
-- If a crash occurs after debit but before idempotency response is stored,
-- this unique constraint prevents duplicate bets on retry.

ALTER TABLE bets ADD COLUMN idempotency_key VARCHAR(36);

-- Unique index ensures at-most-once semantics per idempotency key
CREATE UNIQUE INDEX ux_bets_idempotency_key ON bets(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Comment for documentation
COMMENT ON COLUMN bets.idempotency_key IS 'UUID idempotency key from Idempotency-Key header. Ensures at-most-once bet placement even after crash recovery.';
