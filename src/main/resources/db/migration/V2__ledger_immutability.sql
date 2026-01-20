-- Trigger to enforce append-only ledger (no UPDATE or DELETE allowed)
-- This is a critical security control for betting systems

CREATE OR REPLACE FUNCTION prevent_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger entries are immutable. UPDATE and DELETE operations are forbidden.';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_immutability_guard
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_modification();

COMMENT ON TABLE ledger_entries IS 
    'Append-only audit trail. Updates and deletes blocked by trigger for betting compliance.';

COMMENT ON TRIGGER ledger_immutability_guard ON ledger_entries IS
    'Security control: prevents tampering with financial audit trail.';
