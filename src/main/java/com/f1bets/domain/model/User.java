package com.f1bets.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class User {

    private static final long INITIAL_BALANCE_CENTS = 10_000L;

    private final UserId id;
    private Money balance;
    private long version;
    private final Instant createdAt;

    private User(UserId id, Money balance, long version, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "User ID cannot be null");
        this.balance = Objects.requireNonNull(balance, "Balance cannot be null");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
    }

    public static User createNew(UserId id) {
        return new User(
            id,
            Money.ofCents(INITIAL_BALANCE_CENTS),
            0L,
            Instant.now()
        );
    }

    public static User reconstitute(UserId id, Money balance, long version, Instant createdAt) {
        return new User(id, balance, version, createdAt);
    }

    public void deductBalance(Money amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Deduction amount must be positive");
        }
        if (this.balance.isLessThan(amount)) {
            throw new IllegalStateException(
                "Insufficient balance: " + this.balance + ", required: " + amount
            );
        }
        this.balance = this.balance.subtract(amount);
    }

    public void addWinnings(Money amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Winnings amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }

    public boolean canAfford(Money amount) {
        return this.balance.isGreaterThanOrEqual(amount);
    }

    public UserId getId() {
        return id;
    }

    public Money getBalance() {
        return balance;
    }

    public long getBalanceCents() {
        return balance.toCents();
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", balance=" + balance + "}";
    }
}
