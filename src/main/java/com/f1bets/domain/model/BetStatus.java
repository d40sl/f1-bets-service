package com.f1bets.domain.model;

/**
 * Bet status enumeration.
 */
public enum BetStatus {
    /**
     * Bet is placed but event not yet settled.
     */
    PENDING,

    /**
     * Bet won - payout credited to user.
     */
    WON,

    /**
     * Bet lost - no payout.
     */
    LOST
}
