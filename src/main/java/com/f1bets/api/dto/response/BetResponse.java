package com.f1bets.api.dto.response;

import com.f1bets.application.dto.PlaceBetResult;
import com.f1bets.domain.model.Bet;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BetResponse(
    UUID betId,
    int sessionKey,
    int driverNumber,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal stake,
    int odds,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal potentialWinnings,
    String status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal userBalance
) {

    public static BetResponse from(PlaceBetResult result) {
        return new BetResponse(
            result.betId(),
            result.sessionKey(),
            result.driverNumber(),
            result.stake().toDecimal(),
            result.odds(),
            result.potentialWinnings().toDecimal(),
            result.status(),
            result.userBalance().toDecimal()
        );
    }

    public static BetResponse fromBet(Bet bet) {
        return new BetResponse(
            bet.getId(),
            bet.getSessionKey().getValue(),
            bet.getDriverNumber().getValue(),
            bet.getStake().toDecimal(),
            bet.getOddsValue(),
            bet.calculatePayout().toDecimal(),
            bet.getStatus().name(),
            null
        );
    }
}
