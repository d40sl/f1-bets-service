-- Users table with balance constraint
CREATE TABLE users (
    id VARCHAR(100) PRIMARY KEY,
    balance_cents BIGINT NOT NULL DEFAULT 10000,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT balance_non_negative CHECK (balance_cents >= 0)
);

-- Append-only ledger for audit trail
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL REFERENCES users(id),
    entry_type VARCHAR(20) NOT NULL,
    amount_cents BIGINT NOT NULL,
    balance_after_cents BIGINT NOT NULL,
    reference_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT entry_type_valid CHECK (entry_type IN 
        ('INITIAL_CREDIT', 'BET_PLACED', 'BET_WON', 'BET_LOST'))
);

-- Bets table
CREATE TABLE bets (
    id UUID PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL REFERENCES users(id),
    session_key INT NOT NULL,
    driver_number INT NOT NULL,
    stake_cents BIGINT NOT NULL,
    odds INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT stake_positive CHECK (stake_cents > 0),
    CONSTRAINT stake_max CHECK (stake_cents <= 1000000),
    CONSTRAINT odds_valid CHECK (odds IN (2, 3, 4)),
    CONSTRAINT status_valid CHECK (status IN ('PENDING', 'WON', 'LOST'))
);

-- Event outcomes (PK prevents double settlement)
CREATE TABLE event_outcomes (
    session_key INT PRIMARY KEY,
    winning_driver_number INT NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Idempotency keys for replay protection
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(100),
    request_hash VARCHAR(64) NOT NULL,
    response_payload TEXT NOT NULL,
    response_status INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_ledger_user ON ledger_entries(user_id);
CREATE INDEX idx_ledger_created ON ledger_entries(created_at);
CREATE INDEX idx_bets_session_status ON bets(session_key, status);
CREATE INDEX idx_bets_user ON bets(user_id);
CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);
