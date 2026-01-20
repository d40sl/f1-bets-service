package com.f1bets.infrastructure.persistence.repository;

import com.f1bets.domain.model.Bet;
import com.f1bets.domain.model.BetStatus;
import com.f1bets.domain.model.SessionKey;
import com.f1bets.domain.model.UserId;
import com.f1bets.domain.repository.BetRepository;
import com.f1bets.infrastructure.persistence.entity.BetJpaEntity.BetStatusJpa;
import com.f1bets.infrastructure.persistence.mapper.EntityMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaBetRepository implements BetRepository {

    private final SpringDataBetRepository springDataRepository;
    private final EntityMapper mapper;

    public JpaBetRepository(SpringDataBetRepository springDataRepository, EntityMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Bet> findById(UUID id) {
        return springDataRepository.findById(id)
            .map(mapper::toDomain);
    }

    @Override
    public Optional<Bet> findByIdempotencyKey(String idempotencyKey) {
        return springDataRepository.findByIdempotencyKey(idempotencyKey)
            .map(mapper::toDomain);
    }

    @Override
    public List<Bet> findByUserId(UserId userId) {
        return springDataRepository.findByUserId(userId.getValue())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<Bet> findBySessionKey(SessionKey sessionKey) {
        return springDataRepository.findBySessionKey(sessionKey.getValue())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<Bet> findBySessionKeyAndStatus(SessionKey sessionKey, BetStatus status) {
        return springDataRepository.findBySessionKeyAndStatus(
                sessionKey.getValue(),
                toJpaStatus(status)
            )
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<Bet> findBySessionKeyAndStatusForUpdate(SessionKey sessionKey, BetStatus status) {
        return springDataRepository.findBySessionKeyAndStatusForUpdate(
                sessionKey.getValue(),
                toJpaStatus(status)
            )
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Bet save(Bet bet) {
        var entity = mapper.toJpa(bet);
        var saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<Bet> saveAll(List<Bet> bets) {
        var entities = bets.stream()
            .map(mapper::toJpa)
            .toList();
        var saved = springDataRepository.saveAll(entities);
        return saved.stream()
            .map(mapper::toDomain)
            .toList();
    }

    private static BetStatusJpa toJpaStatus(BetStatus status) {
        return BetStatusJpa.valueOf(status.name());
    }
}
