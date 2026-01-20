package com.f1bets.infrastructure.persistence.repository;

import com.f1bets.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface SpringDataIdempotencyKeyRepository extends JpaRepository<IdempotencyKeyJpaEntity, String> {

    @Modifying
    @Query("DELETE FROM IdempotencyKeyJpaEntity k WHERE k.expiresAt < :now")
    int deleteExpiredKeys(Instant now);

    @Modifying
    @Query("DELETE FROM IdempotencyKeyJpaEntity k WHERE k.status = 'IN_PROGRESS' AND k.createdAt < :staleThreshold")
    int deleteStaleInProgressKeys(Instant staleThreshold);
}
