package com.f1bets.api.dto.response;

import com.f1bets.application.usecase.GetUserUseCase.UserWithBets;

import java.math.BigDecimal;
import java.util.List;

public record UserResponse(
    String userId,
    BigDecimal balance,
    List<BetResponse> bets
) {

    public static UserResponse from(UserWithBets userWithBets) {
        return new UserResponse(
            userWithBets.user().getId().getValue(),
            userWithBets.user().getBalance().toDecimal(),
            userWithBets.bets().stream()
                .map(BetResponse::fromBet)
                .toList()
        );
    }
}
