package com.f1bets.domain.repository;

import com.f1bets.domain.model.LedgerEntry;
import com.f1bets.domain.model.Money;
import com.f1bets.domain.model.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerRepository {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> saveAll(List<LedgerEntry> entries);

    List<LedgerEntry> findByUserId(UserId userId);

    Optional<Money> findBalanceAfterForBet(UUID betId);
}
