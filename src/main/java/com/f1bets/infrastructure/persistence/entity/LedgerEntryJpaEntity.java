package com.f1bets.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntryJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LedgerEntryTypeJpa entryType;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "balance_after_cents", nullable = false)
    private long balanceAfterCents;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntryJpaEntity() {}

    public LedgerEntryJpaEntity(UUID id, String userId, LedgerEntryTypeJpa entryType,
                                long amountCents, long balanceAfterCents,
                                String referenceId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.entryType = entryType;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LedgerEntryTypeJpa getEntryType() {
        return entryType;
    }

    public void setEntryType(LedgerEntryTypeJpa entryType) {
        this.entryType = entryType;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }

    public void setBalanceAfterCents(long balanceAfterCents) {
        this.balanceAfterCents = balanceAfterCents;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public enum LedgerEntryTypeJpa {
        INITIAL_CREDIT, BET_PLACED, BET_WON, BET_LOST
    }
}
