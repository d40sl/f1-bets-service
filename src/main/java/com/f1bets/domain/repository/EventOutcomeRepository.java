package com.f1bets.domain.repository;

import com.f1bets.domain.model.EventOutcome;
import com.f1bets.domain.model.SessionKey;

import java.util.Optional;

public interface EventOutcomeRepository {

    Optional<EventOutcome> findBySessionKey(SessionKey sessionKey);

    EventOutcome save(EventOutcome outcome);

    boolean existsBySessionKey(SessionKey sessionKey);
}
