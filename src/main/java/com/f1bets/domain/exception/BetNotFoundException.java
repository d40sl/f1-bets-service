package com.f1bets.domain.exception;

import java.util.UUID;

public class BetNotFoundException extends RuntimeException {

    private final UUID betId;

    public BetNotFoundException(UUID betId) {
        super("Bet not found: id=" + betId);
        this.betId = betId;
    }

    public UUID getBetId() {
        return betId;
    }
}
