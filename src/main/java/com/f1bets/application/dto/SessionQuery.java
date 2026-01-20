package com.f1bets.application.dto;

/**
 * Query parameters for filtering F1 sessions.
 */
public record SessionQuery(
    String sessionType,
    Integer year,
    String countryCode
) {
    public static SessionQuery of(String sessionType, Integer year, String countryCode) {
        return new SessionQuery(sessionType, year, countryCode);
    }
}
