package com.f1bets.application.usecase;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.SettleEventCommand;
import com.f1bets.application.dto.SettleEventResult;
import com.f1bets.application.port.F1DataProvider;
import com.f1bets.application.port.SessionLock;
import com.f1bets.domain.exception.DriverNotInSessionException;
import com.f1bets.domain.exception.EventAlreadySettledException;
import com.f1bets.domain.exception.EventNotEndedException;
import com.f1bets.domain.exception.SessionNotFoundException;
import com.f1bets.domain.model.Bet;
import com.f1bets.domain.model.BetStatus;
import com.f1bets.domain.model.DriverNumber;
import com.f1bets.domain.model.EventOutcome;
import com.f1bets.domain.model.LedgerEntry;
import com.f1bets.domain.model.Money;
import com.f1bets.domain.model.SessionKey;
import com.f1bets.domain.model.User;
import com.f1bets.domain.repository.BetRepository;
import com.f1bets.domain.repository.EventOutcomeRepository;
import com.f1bets.domain.repository.LedgerRepository;
import com.f1bets.domain.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SettleEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(SettleEventUseCase.class);

    private final EventOutcomeRepository eventOutcomeRepository;
    private final BetRepository betRepository;
    private final UserRepository userRepository;
    private final LedgerRepository ledgerRepository;
    private final SessionLock sessionLock;
    private final F1DataProvider f1DataProvider;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public SettleEventUseCase(EventOutcomeRepository eventOutcomeRepository,
                             BetRepository betRepository,
                             UserRepository userRepository,
                             LedgerRepository ledgerRepository,
                             SessionLock sessionLock,
                             F1DataProvider f1DataProvider,
                             Clock clock,
                             TransactionTemplate transactionTemplate) {
        this.eventOutcomeRepository = eventOutcomeRepository;
        this.betRepository = betRepository;
        this.userRepository = userRepository;
        this.ledgerRepository = ledgerRepository;
        this.sessionLock = sessionLock;
        this.f1DataProvider = f1DataProvider;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Settles an event by declaring the winning driver and processing all bets.
     *
     * The method is structured to minimize transaction duration:
     * 1. HTTP validation (event ended, driver participated) happens OUTSIDE the transaction
     * 2. Session lock is acquired to prevent race with bet placement
     * 3. DB operations happen in a short transaction
     */
    public SettleEventResult execute(SettleEventCommand command) {
        // Step 1: Validate event ended and driver via HTTP OUTSIDE transaction
        // This avoids holding DB connections during potentially slow network calls
        // Uses skipCache=true to get fresh data for financial decisions
        validateEventEndedAndDriver(command.sessionKey(), command.winningDriverNumber());

        // Step 2: Execute DB operations in a short transaction with session lock
        return transactionTemplate.execute(status -> {
            sessionLock.acquire(command.sessionKey());

            // Check if already settled - if same winner, return idempotent success
            var existingOutcome = eventOutcomeRepository.findBySessionKey(command.sessionKey());
            if (existingOutcome.isPresent()) {
                EventOutcome existing = existingOutcome.get();
                if (existing.getWinningDriverNumber().equals(command.winningDriverNumber())) {
                    // Idempotent: same outcome requested, return success
                    log.info("Event {} already settled with same winner {}, returning idempotent success",
                        command.sessionKey(), command.winningDriverNumber());
                    return createIdempotentResult(command);
                }
                // Different winner requested - this is a conflict
                throw new EventAlreadySettledException(command.sessionKey());
            }

            EventOutcome outcome = EventOutcome.create(
                command.sessionKey(),
                command.winningDriverNumber()
            );

            try {
                eventOutcomeRepository.save(outcome);
            } catch (DataIntegrityViolationException e) {
                log.debug("Event {} already settled by concurrent request", command.sessionKey());
                throw new EventAlreadySettledException(command.sessionKey());
            }

            List<Bet> pendingBets = betRepository.findBySessionKeyAndStatusForUpdate(
                command.sessionKey(),
                BetStatus.PENDING
            );

            if (pendingBets.isEmpty()) {
                log.info("Event {} settled with no pending bets", command.sessionKey());
                return new SettleEventResult(
                    command.sessionKey().getValue(),
                    command.winningDriverNumber().getValue(),
                    0,
                    0,
                    Money.ofCents(0)
                );
            }

            List<Bet> sortedBets = pendingBets.stream()
                .sorted(Comparator.comparing(bet -> bet.getUserId().getValue()))
                .toList();

            long totalPayoutCents = 0;
            int winningBetsCount = 0;
            List<LedgerEntry> ledgerEntries = new ArrayList<>();

            for (Bet bet : sortedBets) {
                if (!bet.isPending()) {
                    log.warn("Skipping non-pending bet {} during settlement (status={})",
                             bet.getId(), bet.getStatus());
                    continue;
                }

                User user = userRepository.findByIdForUpdate(bet.getUserId())
                    .orElseThrow(() -> new IllegalStateException("User not found: " + bet.getUserId()));

                if (bet.isForDriver(command.winningDriverNumber())) {
                    bet.markAsWon();
                    Money payout = bet.calculatePayout();
                    totalPayoutCents = Math.addExact(totalPayoutCents, payout.toCents());
                    winningBetsCount++;

                    user.addWinnings(payout);
                    userRepository.save(user);

                    ledgerEntries.add(LedgerEntry.betWon(
                        bet.getUserId(),
                        payout.toCents(),
                        user.getBalanceCents(),
                        bet.getId()
                    ));
                } else {
                    bet.markAsLost();

                    ledgerEntries.add(LedgerEntry.betLost(
                        bet.getUserId(),
                        user.getBalanceCents(),
                        bet.getId()
                    ));
                }
            }

            betRepository.saveAll(sortedBets);
            ledgerRepository.saveAll(ledgerEntries);

            return new SettleEventResult(
                command.sessionKey().getValue(),
                command.winningDriverNumber().getValue(),
                sortedBets.size(),
                winningBetsCount,
                Money.ofCents(totalPayoutCents)
            );
        });
    }

    /**
     * Creates a result for idempotent re-settlement requests (when event was already settled with same winner).
     * Returns settlement totals without modifying any state.
     */
    private SettleEventResult createIdempotentResult(SettleEventCommand command) {
        List<Bet> bets = betRepository.findBySessionKey(command.sessionKey());
        int totalBets = bets.size();
        int winningBets = 0;
        long totalPayoutCents = 0;

        for (Bet bet : bets) {
            if (bet.isWon()) {
                winningBets++;
                totalPayoutCents = Math.addExact(totalPayoutCents, bet.calculatePayout().toCents());
            }
        }

        return new SettleEventResult(
            command.sessionKey().getValue(),
            command.winningDriverNumber().getValue(),
            totalBets,
            winningBets,
            Money.ofCents(totalPayoutCents)
        );
    }

    private void validateEventEndedAndDriver(SessionKey sessionKey, DriverNumber winningDriver) {
        // skipCache=true for fresh data when making financial decisions
        EventWithDrivers session = f1DataProvider.getSessionByKey(sessionKey.getValue(), true)
            .orElseThrow(() -> new SessionNotFoundException(sessionKey));

        Instant now = clock.instant();
        if (session.dateEnd() == null || session.dateEnd().isAfter(now)) {
            throw new EventNotEndedException(sessionKey, session.dateEnd(), now);
        }

        boolean driverParticipated = session.drivers().stream()
            .anyMatch(d -> d.driverNumber() == winningDriver.getValue());

        if (!driverParticipated) {
            throw new DriverNotInSessionException(sessionKey, winningDriver);
        }
    }
}
