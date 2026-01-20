package com.f1bets.domain.model;

/**
 * Session Key value object representing an F1 event session.
 * Must be positive integer.
 */
public final class SessionKey {

    private final int value;

    private SessionKey(int value) {
        this.value = value;
    }

    public static SessionKey of(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                "Session key must be positive: " + value
            );
        }
        return new SessionKey(value);
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionKey that)) return false;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
