package com.f1bets.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class EventOutcome {

    private final SessionKey sessionKey;
    private final DriverNumber winningDriverNumber;
    private final Instant settledAt;

    private EventOutcome(SessionKey sessionKey, DriverNumber winningDriverNumber, Instant settledAt) {
        this.sessionKey = Objects.requireNonNull(sessionKey);
        this.winningDriverNumber = Objects.requireNonNull(winningDriverNumber);
        this.settledAt = Objects.requireNonNull(settledAt);
    }

    public static EventOutcome create(SessionKey sessionKey, DriverNumber winningDriverNumber) {
        return new EventOutcome(sessionKey, winningDriverNumber, Instant.now());
    }

    public static EventOutcome reconstitute(SessionKey sessionKey, DriverNumber winningDriverNumber, Instant settledAt) {
        return new EventOutcome(sessionKey, winningDriverNumber, settledAt);
    }

    public SessionKey getSessionKey() {
        return sessionKey;
    }

    public DriverNumber getWinningDriverNumber() {
        return winningDriverNumber;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventOutcome that)) return false;
        return Objects.equals(sessionKey, that.sessionKey);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sessionKey);
    }

    @Override
    public String toString() {
        return "EventOutcome{sessionKey=" + sessionKey + ", winningDriverNumber=" + winningDriverNumber + "}";
    }
}
