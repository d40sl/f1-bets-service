package com.f1bets.infrastructure.persistence.repository;

import com.f1bets.infrastructure.persistence.entity.BetJpaEntity;
import com.f1bets.infrastructure.persistence.entity.BetJpaEntity.BetStatusJpa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataBetRepository extends JpaRepository<BetJpaEntity, UUID> {

    List<BetJpaEntity> findByUserId(String userId);

    Optional<BetJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<BetJpaEntity> findBySessionKey(int sessionKey);

    List<BetJpaEntity> findBySessionKeyAndStatus(int sessionKey, BetStatusJpa status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BetJpaEntity b WHERE b.sessionKey = :sessionKey AND b.status = :status")
    List<BetJpaEntity> findBySessionKeyAndStatusForUpdate(
        @Param("sessionKey") int sessionKey,
        @Param("status") BetStatusJpa status
    );
}
