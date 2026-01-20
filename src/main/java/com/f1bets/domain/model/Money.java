package com.f1bets.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Money represented as integer cents.
 * 
 * Why cents (not BigDecimal):
 * - No rounding: 2500 cents is exactly EUR 25.00
 * - Deterministic: stakeCents * odds = exact payout
 * - Database-native: BIGINT with CHECK constraints
 * - Auditable: ledger entries are unambiguous
 * 
 * BigDecimal appears ONLY at API boundary for JSON parsing.
 */
public final class Money {

    public static final Money ZERO = new Money(0);
    public static final long MAX_STAKE_CENTS = 1_000_000L; // EUR 10,000.00

    private final long cents;

    private Money(long cents) {
        this.cents = cents;
    }

    /**
     * Create Money from cents. Use for domain operations.
     * Rejects negative amounts.
     */
    public static Money ofCents(long cents) {
        if (cents < 0) {
            throw new IllegalArgumentException(
                "Money cannot be negative: " + cents + " cents"
            );
        }
        return new Money(cents);
    }

    /**
     * Create Money for stakes. Must be positive.
     * Use this for bet amounts.
     */
    public static Money forStake(long cents) {
        if (cents <= 0) {
            throw new IllegalArgumentException(
                "Stake must be positive: " + cents + " cents"
            );
        }
        if (cents > MAX_STAKE_CENTS) {
            throw new IllegalArgumentException(
                "Stake exceeds maximum allowed: " + cents + " cents (max: " + MAX_STAKE_CENTS + ")"
            );
        }
        return new Money(cents);
    }

    /**
     * Parse from BigDecimal. ONLY use at API boundary.
     * Converts EUR 25.00 to 2500 cents.
     */
    public static Money fromDecimal(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                "Amount cannot have more than 2 decimal places: " + amount
            );
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "Amount cannot be negative: " + amount
            );
        }
        long cents = amount.movePointRight(2).longValueExact();
        return ofCents(cents);
    }

    /**
     * Parse from BigDecimal for stakes. ONLY use at API boundary.
     * Validates positive amount and max stake.
     */
    public static Money fromDecimalForStake(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                "Amount cannot have more than 2 decimal places: " + amount
            );
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Stake must be positive: " + amount
            );
        }
        long cents = amount.movePointRight(2).longValueExact();
        return forStake(cents);
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "Cannot add null Money");
        return new Money(Math.addExact(this.cents, other.cents));
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "Cannot subtract null Money");
        long result = Math.subtractExact(this.cents, other.cents);
        if (result < 0) {
            throw new IllegalStateException(
                "Insufficient balance: cannot subtract " + other.cents + 
                " cents from " + this.cents + " cents"
            );
        }
        return new Money(result);
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Cannot multiply by negative factor: " + factor);
        }
        return new Money(Math.multiplyExact(this.cents, factor));
    }

    public long toCents() {
        return cents;
    }

    /**
     * Convert to BigDecimal for API responses ONLY.
     * 2500 cents becomes 25.00
     */
    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(cents, 2);
    }

    public boolean isGreaterThanOrEqual(Money other) {
        Objects.requireNonNull(other, "Cannot compare to null Money");
        return this.cents >= other.cents;
    }

    public boolean isLessThan(Money other) {
        Objects.requireNonNull(other, "Cannot compare to null Money");
        return this.cents < other.cents;
    }

    public boolean isZero() {
        return this.cents == 0;
    }

    public boolean isPositive() {
        return this.cents > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return cents == money.cents;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(cents);
    }

    @Override
    public String toString() {
        return "EUR " + toDecimal().toPlainString();
    }
}
