package com.f1bets.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Request to place a bet on a driver to win an F1 session")
public record PlaceBetRequest(
    @Schema(
        description = "F1 session identifier from OpenF1 API",
        example = "9158",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Session key is required")
    @Positive(message = "Session key must be positive")
    Integer sessionKey,

    @Schema(
        description = "Driver's racing number (1-99). Common numbers: 1=Verstappen, 44=Hamilton, 16=Leclerc, 55=Sainz, 4=Norris, 63=Russell, 11=Perez, 14=Alonso",
        example = "1",
        minimum = "1",
        maximum = "99",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Driver number is required")
    @Positive(message = "Driver number must be positive")
    @Max(value = 99, message = "Driver number cannot exceed 99")
    Integer driverNumber,

    @Schema(
        description = "Bet amount in EUR. New users start with EUR 100.00 balance",
        example = "25.00",
        minimum = "0.01",
        maximum = "10000.00",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @DecimalMin(value = "0.01", message = "Minimum bet is EUR 0.01")
    @DecimalMax(value = "10000.00", message = "Maximum bet is EUR 10,000.00")
    BigDecimal amount
) {}
