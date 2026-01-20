-- Prevent double INITIAL_CREDIT entries for the same user
-- This is a safety net for concurrent first-bet race conditions
CREATE UNIQUE INDEX idx_ledger_one_initial_credit_per_user
ON ledger_entries(user_id)
WHERE entry_type = 'INITIAL_CREDIT';

COMMENT ON INDEX idx_ledger_one_initial_credit_per_user IS
    'Ensures each user receives exactly one initial credit, preventing race condition exploits.';
