package com.f1bets.infrastructure.persistence.mapper;

import com.f1bets.domain.model.Bet;
import com.f1bets.domain.model.BetStatus;
import com.f1bets.domain.model.DriverNumber;
import com.f1bets.domain.model.EventOutcome;
import com.f1bets.domain.model.LedgerEntry;
import com.f1bets.domain.model.LedgerEntryType;
import com.f1bets.domain.model.Money;
import com.f1bets.domain.model.Odds;
import com.f1bets.domain.model.SessionKey;
import com.f1bets.domain.model.User;
import com.f1bets.domain.model.UserId;
import com.f1bets.infrastructure.persistence.entity.BetJpaEntity;
import com.f1bets.infrastructure.persistence.entity.BetJpaEntity.BetStatusJpa;
import com.f1bets.infrastructure.persistence.entity.EventOutcomeJpaEntity;
import com.f1bets.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import com.f1bets.infrastructure.persistence.entity.LedgerEntryJpaEntity.LedgerEntryTypeJpa;
import com.f1bets.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class EntityMapper {

    public User toDomain(UserJpaEntity entity) {
        return User.reconstitute(
            UserId.of(entity.getId()),
            Money.ofCents(entity.getBalanceCents()),
            entity.getVersion(),
            entity.getCreatedAt()
        );
    }

    public UserJpaEntity toJpa(User user) {
        return new UserJpaEntity(
            user.getId().getValue(),
            user.getBalanceCents(),
            user.getVersion(),
            user.getCreatedAt()
        );
    }

    public Bet toDomain(BetJpaEntity entity) {
        return Bet.reconstitute(
            entity.getId(),
            UserId.of(entity.getUserId()),
            SessionKey.of(entity.getSessionKey()),
            DriverNumber.of(entity.getDriverNumber()),
            Money.ofCents(entity.getStakeCents()),
            Odds.of(entity.getOdds()),
            toBetStatus(entity.getStatus()),
            entity.getCreatedAt(),
            entity.getSettledAt(),
            entity.getIdempotencyKey()
        );
    }

    public BetJpaEntity toJpa(Bet bet) {
        return new BetJpaEntity(
            bet.getId(),
            bet.getUserId().getValue(),
            bet.getSessionKey().getValue(),
            bet.getDriverNumber().getValue(),
            bet.getStakeCents(),
            bet.getOddsValue(),
            toBetStatusJpa(bet.getStatus()),
            bet.getCreatedAt(),
            bet.getSettledAt(),
            bet.getIdempotencyKey()
        );
    }

    public LedgerEntry toDomain(LedgerEntryJpaEntity entity) {
        return LedgerEntry.reconstitute(
            entity.getId(),
            UserId.of(entity.getUserId()),
            toLedgerEntryType(entity.getEntryType()),
            entity.getAmountCents(),
            entity.getBalanceAfterCents(),
            entity.getReferenceId(),
            entity.getCreatedAt()
        );
    }

    public LedgerEntryJpaEntity toJpa(LedgerEntry entry) {
        return new LedgerEntryJpaEntity(
            entry.getId(),
            entry.getUserId().getValue(),
            toLedgerEntryTypeJpa(entry.getEntryType()),
            entry.getAmountCents(),
            entry.getBalanceAfterCents(),
            entry.getReferenceId(),
            entry.getCreatedAt()
        );
    }

    public EventOutcome toDomain(EventOutcomeJpaEntity entity) {
        return EventOutcome.reconstitute(
            SessionKey.of(entity.getSessionKey()),
            DriverNumber.of(entity.getWinningDriverNumber()),
            entity.getSettledAt()
        );
    }

    public EventOutcomeJpaEntity toJpa(EventOutcome outcome) {
        return new EventOutcomeJpaEntity(
            outcome.getSessionKey().getValue(),
            outcome.getWinningDriverNumber().getValue(),
            outcome.getSettledAt()
        );
    }

    private BetStatus toBetStatus(BetStatusJpa status) {
        return BetStatus.valueOf(status.name());
    }

    private BetStatusJpa toBetStatusJpa(BetStatus status) {
        return BetStatusJpa.valueOf(status.name());
    }

    private LedgerEntryType toLedgerEntryType(LedgerEntryTypeJpa type) {
        return LedgerEntryType.valueOf(type.name());
    }

    private LedgerEntryTypeJpa toLedgerEntryTypeJpa(LedgerEntryType type) {
        return LedgerEntryTypeJpa.valueOf(type.name());
    }
}
