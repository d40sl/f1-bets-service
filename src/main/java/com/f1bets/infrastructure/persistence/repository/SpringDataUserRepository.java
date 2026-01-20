package com.f1bets.infrastructure.persistence.repository;

import com.f1bets.infrastructure.persistence.entity.UserJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserJpaEntity u WHERE u.id = :id")
    Optional<UserJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Modifying
    @Query(
        value = "INSERT INTO users (id, balance_cents, version, created_at) " +
                "VALUES (:id, :balanceCents, :version, :createdAt) " +
                "ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    int insertIfAbsent(@Param("id") String id,
                       @Param("balanceCents") long balanceCents,
                       @Param("version") long version,
                       @Param("createdAt") java.time.Instant createdAt);
}
