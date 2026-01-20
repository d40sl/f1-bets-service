package com.f1bets.infrastructure.persistence.repository;

import com.f1bets.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import com.f1bets.infrastructure.persistence.entity.LedgerEntryJpaEntity.LedgerEntryTypeJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataLedgerRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {

    List<LedgerEntryJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<LedgerEntryJpaEntity> findTopByReferenceIdAndEntryTypeOrderByCreatedAtDesc(
        String referenceId,
        LedgerEntryTypeJpa entryType
    );
}
