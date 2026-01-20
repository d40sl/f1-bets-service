package com.f1bets.application.dto;

import com.f1bets.domain.model.DriverNumber;
import com.f1bets.domain.model.Money;
import com.f1bets.domain.model.SessionKey;
import com.f1bets.domain.model.UserId;

import java.util.Objects;

public record PlaceBetCommand(
    UserId userId,
    SessionKey sessionKey,
    DriverNumber driverNumber,
    Money stake,
    String idempotencyKey
) {
    public PlaceBetCommand {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(sessionKey, "Session key cannot be null");
        Objects.requireNonNull(driverNumber, "Driver number cannot be null");
        Objects.requireNonNull(stake, "Stake cannot be null");
    }

    public PlaceBetCommand(UserId userId, SessionKey sessionKey, DriverNumber driverNumber, Money stake) {
        this(userId, sessionKey, driverNumber, stake, null);
    }
}
