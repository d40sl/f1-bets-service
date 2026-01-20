package com.f1bets.api.dto.response;

import com.f1bets.application.dto.EventWithDrivers;

import java.time.Instant;
import java.util.List;

public record EventResponse(
    int sessionKey,
    String sessionName,
    String sessionType,
    String circuitName,
    String countryName,
    String countryCode,
    Instant dateStart,
    Instant dateEnd,
    int year,
    List<DriverResponse> drivers
) {

    public static EventResponse from(EventWithDrivers event) {
        return new EventResponse(
            event.sessionKey(),
            event.sessionName(),
            event.sessionType(),
            event.circuitName(),
            event.countryName(),
            event.countryCode(),
            event.dateStart(),
            event.dateEnd(),
            event.year(),
            event.drivers().stream()
                .map(d -> new DriverResponse(
                    d.driverNumber(),
                    d.fullName(),
                    d.teamName(),
                    d.odds()
                ))
                .toList()
        );
    }

    public record DriverResponse(
        int driverNumber,
        String fullName,
        String teamName,
        int odds
    ) {}
}
