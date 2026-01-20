package com.f1bets.domain.model;

/**
 * Ledger entry type enumeration.
 * All balance changes must be recorded with one of these types.
 */
public enum LedgerEntryType {
    /**
     * Initial credit given to user on registration (EUR 100).
     */
    INITIAL_CREDIT,

    /**
     * Stake deducted when bet is placed.
     */
    BET_PLACED,

    /**
     * Winnings credited when bet wins.
     */
    BET_WON,

    /**
     * Recorded when bet loses (no balance change, for audit trail).
     */
    BET_LOST
}
