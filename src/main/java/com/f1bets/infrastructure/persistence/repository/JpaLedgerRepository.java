package com.f1bets.infrastructure.persistence.repository;

import com.f1bets.domain.model.LedgerEntry;
import com.f1bets.domain.model.Money;
import com.f1bets.domain.model.UserId;
import com.f1bets.domain.repository.LedgerRepository;
import com.f1bets.infrastructure.persistence.entity.LedgerEntryJpaEntity.LedgerEntryTypeJpa;
import com.f1bets.infrastructure.persistence.mapper.EntityMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaLedgerRepository implements LedgerRepository {

    private final SpringDataLedgerRepository springDataRepository;
    private final EntityMapper mapper;

    public JpaLedgerRepository(SpringDataLedgerRepository springDataRepository, EntityMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        var entity = mapper.toJpa(entry);
        var saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<LedgerEntry> saveAll(List<LedgerEntry> entries) {
        var entities = entries.stream()
            .map(mapper::toJpa)
            .toList();
        var saved = springDataRepository.saveAll(entities);
        return saved.stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<LedgerEntry> findByUserId(UserId userId) {
        return springDataRepository.findByUserIdOrderByCreatedAtDesc(userId.getValue())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Optional<Money> findBalanceAfterForBet(UUID betId) {
        return springDataRepository
            .findTopByReferenceIdAndEntryTypeOrderByCreatedAtDesc(
                betId.toString(),
                LedgerEntryTypeJpa.BET_PLACED
            )
            .map(entry -> Money.ofCents(entry.getBalanceAfterCents()));
    }
}
