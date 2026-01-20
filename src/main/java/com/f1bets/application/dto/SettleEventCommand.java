package com.f1bets.application.dto;

import com.f1bets.domain.model.DriverNumber;
import com.f1bets.domain.model.SessionKey;

import java.util.Objects;

/**
 * Command to settle an F1 event with the winning driver.
 */
public record SettleEventCommand(
    SessionKey sessionKey,
    DriverNumber winningDriverNumber
) {
    public SettleEventCommand {
        Objects.requireNonNull(sessionKey, "Session key cannot be null");
        Objects.requireNonNull(winningDriverNumber, "Winning driver number cannot be null");
    }
}
