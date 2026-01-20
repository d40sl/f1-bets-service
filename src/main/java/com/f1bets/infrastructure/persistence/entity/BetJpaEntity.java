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
@Table(name = "bets")
public class BetJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "session_key", nullable = false)
    private int sessionKey;

    @Column(name = "driver_number", nullable = false)
    private int driverNumber;

    @Column(name = "stake_cents", nullable = false)
    private long stakeCents;

    @Column(name = "odds", nullable = false)
    private int odds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BetStatusJpa status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "idempotency_key", length = 36)
    private String idempotencyKey;

    protected BetJpaEntity() {}

    public BetJpaEntity(UUID id, String userId, int sessionKey, int driverNumber,
                        long stakeCents, int odds, BetStatusJpa status,
                        Instant createdAt, Instant settledAt, String idempotencyKey) {
        this.id = id;
        this.userId = userId;
        this.sessionKey = sessionKey;
        this.driverNumber = driverNumber;
        this.stakeCents = stakeCents;
        this.odds = odds;
        this.status = status;
        this.createdAt = createdAt;
        this.settledAt = settledAt;
        this.idempotencyKey = idempotencyKey;
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

    public int getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(int sessionKey) {
        this.sessionKey = sessionKey;
    }

    public int getDriverNumber() {
        return driverNumber;
    }

    public void setDriverNumber(int driverNumber) {
        this.driverNumber = driverNumber;
    }

    public long getStakeCents() {
        return stakeCents;
    }

    public void setStakeCents(long stakeCents) {
        this.stakeCents = stakeCents;
    }

    public int getOdds() {
        return odds;
    }

    public void setOdds(int odds) {
        this.odds = odds;
    }

    public BetStatusJpa getStatus() {
        return status;
    }

    public void setStatus(BetStatusJpa status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public enum BetStatusJpa {
        PENDING, WON, LOST
    }
}
