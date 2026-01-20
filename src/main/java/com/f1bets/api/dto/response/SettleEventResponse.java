package com.f1bets.api.dto.response;

import com.f1bets.application.dto.SettleEventResult;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record SettleEventResponse(
    int sessionKey,
    int winningDriverNumber,
    int totalBets,
    int winningBets,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalPayout
) {

    public static SettleEventResponse from(SettleEventResult result) {
        return new SettleEventResponse(
            result.sessionKey(),
            result.winningDriverNumber(),
            result.totalBets(),
            result.winningBets(),
            result.totalPayout().toDecimal()
        );
    }
}
