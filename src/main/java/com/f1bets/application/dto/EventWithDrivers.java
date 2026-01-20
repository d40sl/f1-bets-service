package com.f1bets.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Event with participating drivers and their odds.
 */
public record EventWithDrivers(
    int sessionKey,
    String sessionName,
    String sessionType,
    String circuitName,
    String countryName,
    String countryCode,
    Instant dateStart,
    Instant dateEnd,
    int year,
    List<DriverInfo> drivers
) {
    public EventWithDrivers {
        Objects.requireNonNull(sessionName);
        Objects.requireNonNull(sessionType);
        Objects.requireNonNull(drivers);
    }

    /**
     * Driver information with odds.
     */
    public record DriverInfo(
        int driverNumber,
        String fullName,
        String teamName,
        int odds
    ) {}
}
