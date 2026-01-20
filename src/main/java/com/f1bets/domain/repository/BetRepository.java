package com.f1bets.domain.repository;

import com.f1bets.domain.model.Bet;
import com.f1bets.domain.model.BetStatus;
import com.f1bets.domain.model.SessionKey;
import com.f1bets.domain.model.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BetRepository {

    Optional<Bet> findById(UUID id);

    Optional<Bet> findByIdempotencyKey(String idempotencyKey);

    List<Bet> findByUserId(UserId userId);

    List<Bet> findBySessionKey(SessionKey sessionKey);

    List<Bet> findBySessionKeyAndStatus(SessionKey sessionKey, BetStatus status);

    List<Bet> findBySessionKeyAndStatusForUpdate(SessionKey sessionKey, BetStatus status);

    Bet save(Bet bet);

    List<Bet> saveAll(List<Bet> bets);
}
