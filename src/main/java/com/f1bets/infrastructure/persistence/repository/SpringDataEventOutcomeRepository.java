package com.f1bets.infrastructure.persistence.repository;

import com.f1bets.infrastructure.persistence.entity.EventOutcomeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataEventOutcomeRepository extends JpaRepository<EventOutcomeJpaEntity, Integer> {

    boolean existsBySessionKey(int sessionKey);
}
