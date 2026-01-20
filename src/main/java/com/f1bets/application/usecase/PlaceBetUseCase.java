package com.f1bets.application.usecase;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.PlaceBetCommand;
import com.f1bets.application.dto.PlaceBetResult;
import com.f1bets.application.port.F1DataProvider;
import com.f1bets.application.port.SessionLock;
import com.f1bets.application.service.OddsCalculator;
import com.f1bets.domain.exception.DriverNotInSessionException;
import com.f1bets.domain.exception.EventAlreadySettledException;
import com.f1bets.domain.exception.InsufficientBalanceException;
import com.f1bets.domain.exception.SessionNotFoundException;
import com.f1bets.domain.model.Bet;
import com.f1bets.domain.model.DriverNumber;
import com.f1bets.domain.model.LedgerEntry;
import com.f1bets.domain.model.Odds;
import com.f1bets.domain.model.SessionKey;
import com.f1bets.domain.model.User;
import com.f1bets.domain.repository.BetRepository;
import com.f1bets.domain.repository.EventOutcomeRepository;
import com.f1bets.domain.repository.LedgerRepository;
import com.f1bets.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlaceBetUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlaceBetUseCase.class);

    private final UserRepository userRepository;
    private final BetRepository betRepository;
    private final LedgerRepository ledgerRepository;
    private final EventOutcomeRepository eventOutcomeRepository;
    private final OddsCalculator oddsCalculator;
    private final F1DataProvider f1DataProvider;
    private final SessionLock sessionLock;
    private final TransactionTemplate transactionTemplate;

    public PlaceBetUseCase(UserRepository userRepository,
                          BetRepository betRepository,
                          LedgerRepository ledgerRepository,
                          EventOutcomeRepository eventOutcomeRepository,
                          OddsCalculator oddsCalculator,
                          F1DataProvider f1DataProvider,
                          SessionLock sessionLock,
                          TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.betRepository = betRepository;
        this.ledgerRepository = ledgerRepository;
        this.eventOutcomeRepository = eventOutcomeRepository;
        this.oddsCalculator = oddsCalculator;
        this.f1DataProvider = f1DataProvider;
        this.sessionLock = sessionLock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Places a bet on a driver to win an event.
     *
     * The method is structured to minimize transaction duration:
     * 1. HTTP validation (session/driver) happens OUTSIDE the transaction
     * 2. Session lock is acquired to prevent race with settlement
     * 3. DB operations happen in a short transaction
     */
    public PlaceBetResult execute(PlaceBetCommand command) {
        // Step 1: Check idempotency OUTSIDE transaction (read-only, no lock needed)
        String idempotencyKey = command.idempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existingBet = betRepository.findByIdempotencyKey(idempotencyKey);
            if (existingBet.isPresent()) {
                var balanceAfter = ledgerRepository.findBalanceAfterForBet(existingBet.get().getId())
                    .orElseGet(() -> userRepository.findById(existingBet.get().getUserId())
                        .orElseThrow(() -> new IllegalStateException(
                            "User not found for existing bet: " + existingBet.get().getUserId()))
                        .getBalance());
                return PlaceBetResult.from(existingBet.get(), balanceAfter);
            }
        }

        // Step 2: Validate session and driver via HTTP OUTSIDE transaction
        // This avoids holding DB connections during potentially slow network calls
        validateSessionAndDriver(command.sessionKey(), command.driverNumber());

        // Step 3: Acquire session lock and execute DB operations in a short transaction
        // The session lock prevents race condition with settlement
        return transactionTemplate.execute(status -> {
            sessionLock.acquire(command.sessionKey());

            // Re-check if event was settled while we were validating
            if (eventOutcomeRepository.existsBySessionKey(command.sessionKey())) {
                throw new EventAlreadySettledException(command.sessionKey());
            }

            User user = userRepository.findByIdForUpdate(command.userId())
                .orElseGet(() -> getOrCreateUser(command));

            if (!user.canAfford(command.stake())) {
                throw new InsufficientBalanceException(user.getBalance(), command.stake());
            }

            Odds odds = oddsCalculator.calculate(
                command.sessionKey().getValue(),
                command.driverNumber().getValue()
            );

            user.deductBalance(command.stake());

            Bet bet = Bet.place(
                command.userId(),
                command.sessionKey(),
                command.driverNumber(),
                command.stake(),
                odds,
                command.idempotencyKey()
            );

            LedgerEntry ledgerEntry = LedgerEntry.betPlaced(
                command.userId(),
                command.stake().toCents(),
                user.getBalanceCents(),
                bet.getId()
            );

            userRepository.save(user);
            betRepository.save(bet);
            ledgerRepository.save(ledgerEntry);

            return PlaceBetResult.from(bet, user.getBalance());
        });
    }

    private User getOrCreateUser(PlaceBetCommand command) {
        User newUser = User.createNew(command.userId());
        boolean inserted = userRepository.insertIfAbsent(newUser);
        if (inserted) {
            LedgerEntry initialCredit = LedgerEntry.initialCredit(
                command.userId(),
                newUser.getBalanceCents()
            );
            ledgerRepository.save(initialCredit);
            log.debug("Created new user: {}", command.userId());
            // Return the newly created user directly to avoid race condition
            // where concurrent requests could both create INITIAL_CREDIT entries
            return userRepository.findByIdForUpdate(command.userId())
                .orElseThrow(() -> new IllegalStateException("User creation failed"));
        }
        // User already existed (concurrent creation won), fetch with lock
        return userRepository.findByIdForUpdate(command.userId())
            .orElseThrow(() -> new IllegalStateException("User not found after insertIfAbsent returned false"));
    }

    private void validateSessionAndDriver(SessionKey sessionKey, DriverNumber driverNumber) {
        EventWithDrivers session = f1DataProvider.getSessionByKey(sessionKey.getValue())
            .orElseThrow(() -> new SessionNotFoundException(sessionKey));

        boolean driverParticipated = session.drivers().stream()
            .anyMatch(d -> d.driverNumber() == driverNumber.getValue());

        if (!driverParticipated) {
            throw new DriverNotInSessionException(sessionKey, driverNumber);
        }
    }
}
