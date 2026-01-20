package com.f1bets.infrastructure.persistence.repository;

import com.f1bets.domain.model.EventOutcome;
import com.f1bets.domain.model.SessionKey;
import com.f1bets.domain.repository.EventOutcomeRepository;
import com.f1bets.infrastructure.persistence.mapper.EntityMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaEventOutcomeRepository implements EventOutcomeRepository {

    private final SpringDataEventOutcomeRepository springDataRepository;
    private final EntityMapper mapper;

    public JpaEventOutcomeRepository(SpringDataEventOutcomeRepository springDataRepository, EntityMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<EventOutcome> findBySessionKey(SessionKey sessionKey) {
        return springDataRepository.findById(sessionKey.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public EventOutcome save(EventOutcome outcome) {
        var entity = mapper.toJpa(outcome);
        var saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public boolean existsBySessionKey(SessionKey sessionKey) {
        return springDataRepository.existsBySessionKey(sessionKey.getValue());
    }
}
