package com.f1bets.domain.model;

/**
 * Driver Number value object.
 * Must be positive integer, max 99.
 */
public final class DriverNumber {

    private static final int MAX_VALUE = 99;

    private final int value;

    private DriverNumber(int value) {
        this.value = value;
    }

    public static DriverNumber of(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                "Driver number must be positive: " + value
            );
        }
        if (value > MAX_VALUE) {
            throw new IllegalArgumentException(
                "Driver number exceeds maximum of " + MAX_VALUE + ": " + value
            );
        }
        return new DriverNumber(value);
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DriverNumber that)) return false;
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
