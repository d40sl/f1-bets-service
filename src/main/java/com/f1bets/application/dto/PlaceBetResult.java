package com.f1bets.application.dto;

import com.f1bets.domain.model.Bet;
import com.f1bets.domain.model.Money;

import java.util.UUID;

/**
 * Result of placing a bet.
 */
public record PlaceBetResult(
    UUID betId,
    int sessionKey,
    int driverNumber,
    Money stake,
    int odds,
    Money potentialWinnings,
    String status,
    Money userBalance
) {
    public static PlaceBetResult from(Bet bet, Money userBalance) {
        return new PlaceBetResult(
            bet.getId(),
            bet.getSessionKey().getValue(),
            bet.getDriverNumber().getValue(),
            bet.getStake(),
            bet.getOddsValue(),
            bet.calculatePayout(),
            bet.getStatus().name(),
            userBalance
        );
    }
}
