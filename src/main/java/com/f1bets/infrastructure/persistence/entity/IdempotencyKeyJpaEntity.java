package com.f1bets.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyJpaEntity {

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected IdempotencyKeyJpaEntity() {}

    public IdempotencyKeyJpaEntity(String idempotencyKey, String userId, String requestHash,
                                   String responsePayload, Integer responseStatus,
                                   Instant createdAt, Instant expiresAt, String status) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.requestHash = requestHash;
        this.responsePayload = responsePayload;
        this.responseStatus = responseStatus;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public static IdempotencyKeyJpaEntity createInProgress(String idempotencyKey, String userId,
                                                           String requestHash, Instant createdAt,
                                                           Instant expiresAt) {
        return new IdempotencyKeyJpaEntity(idempotencyKey, userId, requestHash, null, null,
                                           createdAt, expiresAt, STATUS_IN_PROGRESS);
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    public void markCompleted(String responsePayload, int responseStatus) {
        this.status = STATUS_COMPLETED;
        this.responsePayload = responsePayload;
        this.responseStatus = responseStatus;
    }

    public void markFailed(String errorMessage) {
        this.status = STATUS_FAILED;
        this.responsePayload = errorMessage;
        this.responseStatus = 500;
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }
}
