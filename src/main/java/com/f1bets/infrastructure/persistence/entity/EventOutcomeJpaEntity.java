package com.f1bets.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "event_outcomes")
public class EventOutcomeJpaEntity {

    @Id
    @Column(name = "session_key")
    private int sessionKey;

    @Column(name = "winning_driver_number", nullable = false)
    private int winningDriverNumber;

    @Column(name = "settled_at", nullable = false, updatable = false)
    private Instant settledAt;

    protected EventOutcomeJpaEntity() {}

    public EventOutcomeJpaEntity(int sessionKey, int winningDriverNumber, Instant settledAt) {
        this.sessionKey = sessionKey;
        this.winningDriverNumber = winningDriverNumber;
        this.settledAt = settledAt;
    }

    public int getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(int sessionKey) {
        this.sessionKey = sessionKey;
    }

    public int getWinningDriverNumber() {
        return winningDriverNumber;
    }

    public void setWinningDriverNumber(int winningDriverNumber) {
        this.winningDriverNumber = winningDriverNumber;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }
}
