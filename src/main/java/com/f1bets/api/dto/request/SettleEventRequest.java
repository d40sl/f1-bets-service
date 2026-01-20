package com.f1bets.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request to settle an F1 event by declaring the winning driver")
public record SettleEventRequest(
    @Schema(
        description = "The driver number who won the race (1-99). Common numbers: 1=Verstappen, 44=Hamilton, 16=Leclerc",
        example = "1",
        minimum = "1",
        maximum = "99",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Winning driver number is required")
    @Positive(message = "Winning driver number must be positive")
    @Max(value = 99, message = "Driver number cannot exceed 99")
    Integer winningDriverNumber
) {}
