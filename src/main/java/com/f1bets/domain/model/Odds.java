package com.f1bets.domain.model;

import java.util.Objects;
import java.util.Set;

/**
 * Odds value object representing betting multipliers.
 * 
 * Valid values: 2, 3, or 4 only.
 * Odds are SERVER-DERIVED, never client-submitted.
 */
public final class Odds {

    private static final Set<Integer> VALID_ODDS = Set.of(2, 3, 4);

    private final int value;

    private Odds(int value) {
        this.value = value;
    }

    /**
     * Create Odds from integer value.
     * Only accepts 2, 3, or 4.
     */
    public static Odds of(int value) {
        if (!VALID_ODDS.contains(value)) {
            throw new IllegalArgumentException(
                "Invalid odds value: " + value + ". Must be one of: " + VALID_ODDS
            );
        }
        return new Odds(value);
    }

    /**
     * Generate deterministic odds based on session key, driver number, and seed.
     * Same inputs always produce same odds.
     *
     * @param sessionKey F1 session identifier
     * @param driverNumber Driver number
     * @param seed Configurable seed for odds distribution
     */
    public static Odds fromSessionAndDriver(int sessionKey, int driverNumber, String seed) {
        int hash = Objects.hash(sessionKey, driverNumber, seed);
        int[] validValues = {2, 3, 4};
        int index = (hash & 0x7FFFFFFF) % validValues.length;
        return new Odds(validValues[index]);
    }

    public int getValue() {
        return value;
    }

    /**
     * Calculate potential payout for a given stake.
     * Returns stake * odds.
     */
    public Money calculatePayout(Money stake) {
        Objects.requireNonNull(stake, "Stake cannot be null");
        return stake.multiply(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Odds odds)) return false;
        return value == odds.value;
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
