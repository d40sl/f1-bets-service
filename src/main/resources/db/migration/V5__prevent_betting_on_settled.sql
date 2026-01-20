-- Prevent betting on settled events at the database level
-- This guards against race conditions between bet placement and settlement

CREATE OR REPLACE FUNCTION prevent_bet_on_settled_event()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM event_outcomes WHERE session_key = NEW.session_key) THEN
        RAISE EXCEPTION 'Cannot place bet on already settled event: session_key=%', NEW.session_key
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER enforce_no_bet_on_settled
    BEFORE INSERT ON bets
    FOR EACH ROW
    EXECUTE FUNCTION prevent_bet_on_settled_event();
