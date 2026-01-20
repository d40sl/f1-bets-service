package com.f1bets.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class LedgerEntry {

    private final UUID id;
    private final UserId userId;
    private final LedgerEntryType entryType;
    private final long amountCents;
    private final long balanceAfterCents;
    private final String referenceId;
    private final Instant createdAt;

    private LedgerEntry(UUID id, UserId userId, LedgerEntryType entryType,
                        long amountCents, long balanceAfterCents, String referenceId, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.entryType = Objects.requireNonNull(entryType);
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
        this.referenceId = referenceId;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static LedgerEntry initialCredit(UserId userId, long balanceAfterCents) {
        return new LedgerEntry(
            UUID.randomUUID(),
            userId,
            LedgerEntryType.INITIAL_CREDIT,
            balanceAfterCents,
            balanceAfterCents,
            null,
            Instant.now()
        );
    }

    public static LedgerEntry betPlaced(UserId userId, long amountCents, long balanceAfterCents, UUID betId) {
        return new LedgerEntry(
            UUID.randomUUID(),
            userId,
            LedgerEntryType.BET_PLACED,
            -amountCents,
            balanceAfterCents,
            betId.toString(),
            Instant.now()
        );
    }

    public static LedgerEntry betWon(UserId userId, long amountCents, long balanceAfterCents, UUID betId) {
        return new LedgerEntry(
            UUID.randomUUID(),
            userId,
            LedgerEntryType.BET_WON,
            amountCents,
            balanceAfterCents,
            betId.toString(),
            Instant.now()
        );
    }

    public static LedgerEntry betLost(UserId userId, long balanceAfterCents, UUID betId) {
        return new LedgerEntry(
            UUID.randomUUID(),
            userId,
            LedgerEntryType.BET_LOST,
            0,
            balanceAfterCents,
            betId.toString(),
            Instant.now()
        );
    }

    public static LedgerEntry reconstitute(UUID id, UserId userId, LedgerEntryType entryType,
                                           long amountCents, long balanceAfterCents,
                                           String referenceId, Instant createdAt) {
        return new LedgerEntry(id, userId, entryType, amountCents, balanceAfterCents, referenceId, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LedgerEntry that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "LedgerEntry{id=" + id + ", userId=" + userId + ", entryType=" + entryType +
               ", amountCents=" + amountCents + ", balanceAfterCents=" + balanceAfterCents + "}";
    }
}
