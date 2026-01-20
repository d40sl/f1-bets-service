package com.f1bets.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Bet {

    private final UUID id;
    private final UserId userId;
    private final SessionKey sessionKey;
    private final DriverNumber driverNumber;
    private final Money stake;
    private final Odds odds;
    private BetStatus status;
    private final Instant createdAt;
    private Instant settledAt;
    private final String idempotencyKey;

    private Bet(UUID id, UserId userId, SessionKey sessionKey, DriverNumber driverNumber,
                Money stake, Odds odds, BetStatus status, Instant createdAt, Instant settledAt,
                String idempotencyKey) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.sessionKey = Objects.requireNonNull(sessionKey);
        this.driverNumber = Objects.requireNonNull(driverNumber);
        this.stake = Objects.requireNonNull(stake);
        this.odds = Objects.requireNonNull(odds);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.settledAt = settledAt;
        this.idempotencyKey = idempotencyKey;
    }

    public static Bet place(UserId userId, SessionKey sessionKey, DriverNumber driverNumber,
                            Money stake, Odds odds) {
        return place(userId, sessionKey, driverNumber, stake, odds, null);
    }

    public static Bet place(UserId userId, SessionKey sessionKey, DriverNumber driverNumber,
                            Money stake, Odds odds, String idempotencyKey) {
        return new Bet(
            UUID.randomUUID(),
            userId,
            sessionKey,
            driverNumber,
            stake,
            odds,
            BetStatus.PENDING,
            Instant.now(),
            null,
            idempotencyKey
        );
    }

    public static Bet reconstitute(UUID id, UserId userId, SessionKey sessionKey,
                                   DriverNumber driverNumber, Money stake, Odds odds,
                                   BetStatus status, Instant createdAt, Instant settledAt) {
        return new Bet(id, userId, sessionKey, driverNumber, stake, odds, status, createdAt, settledAt, null);
    }

    public static Bet reconstitute(UUID id, UserId userId, SessionKey sessionKey,
                                   DriverNumber driverNumber, Money stake, Odds odds,
                                   BetStatus status, Instant createdAt, Instant settledAt,
                                   String idempotencyKey) {
        return new Bet(id, userId, sessionKey, driverNumber, stake, odds, status, createdAt, settledAt, idempotencyKey);
    }

    public void markAsWon() {
        if (this.status != BetStatus.PENDING) {
            throw new IllegalStateException("Cannot mark bet as won: current status is " + this.status);
        }
        this.status = BetStatus.WON;
        this.settledAt = Instant.now();
    }

    public void markAsLost() {
        if (this.status != BetStatus.PENDING) {
            throw new IllegalStateException("Cannot mark bet as lost: current status is " + this.status);
        }
        this.status = BetStatus.LOST;
        this.settledAt = Instant.now();
    }

    public Money calculatePayout() {
        return odds.calculatePayout(stake);
    }

    public boolean isPending() {
        return status == BetStatus.PENDING;
    }

    public boolean isWon() {
        return status == BetStatus.WON;
    }

    public boolean isForDriver(DriverNumber winningDriver) {
        return this.driverNumber.equals(winningDriver);
    }

    public UUID getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public SessionKey getSessionKey() {
        return sessionKey;
    }

    public DriverNumber getDriverNumber() {
        return driverNumber;
    }

    public Money getStake() {
        return stake;
    }

    public long getStakeCents() {
        return stake.toCents();
    }

    public Odds getOdds() {
        return odds;
    }

    public int getOddsValue() {
        return odds.getValue();
    }

    public BetStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bet bet)) return false;
        return Objects.equals(id, bet.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Bet{id=" + id + ", sessionKey=" + sessionKey + ", driverNumber=" + driverNumber +
               ", stake=" + stake + ", odds=" + odds + ", status=" + status + "}";
    }
}
