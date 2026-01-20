-- Add composite index for ordered queries on ledger_entries
-- Optimizes findByUserIdOrderByCreatedAtDesc query
CREATE INDEX idx_ledger_user_created ON ledger_entries(user_id, created_at DESC);
