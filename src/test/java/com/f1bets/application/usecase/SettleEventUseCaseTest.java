package com.f1bets.application.usecase;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.SettleEventCommand;
import com.f1bets.application.dto.SettleEventResult;
import com.f1bets.application.port.F1DataProvider;
import com.f1bets.application.port.SessionLock;
import com.f1bets.domain.exception.DriverNotInSessionException;
import com.f1bets.domain.exception.EventAlreadySettledException;
import com.f1bets.domain.exception.EventNotEndedException;
import com.f1bets.domain.exception.ExternalServiceUnavailableException;
import com.f1bets.domain.exception.SessionNotFoundException;
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
import com.f1bets.domain.repository.BetRepository;
import com.f1bets.domain.repository.EventOutcomeRepository;
import com.f1bets.domain.repository.LedgerRepository;
import com.f1bets.domain.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettleEventUseCaseTest {

    @Mock
    private EventOutcomeRepository eventOutcomeRepository;

    @Mock
    private BetRepository betRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private SessionLock sessionLock;

    @Mock
    private F1DataProvider f1DataProvider;

    @Mock
    private TransactionTemplate transactionTemplate;

    private Clock fixedClock;

    private SettleEventUseCase settleEventUseCase;

    private SessionKey sessionKey;
    private DriverNumber winningDriver;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Fixed clock set to a time after the default event end time
        fixedClock = Clock.fixed(Instant.parse("2024-06-01T18:00:00Z"), ZoneOffset.UTC);

        // Configure transactionTemplate to execute callbacks synchronously
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
            .thenAnswer(invocation -> {
                TransactionCallback<SettleEventResult> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });

        settleEventUseCase = new SettleEventUseCase(
            eventOutcomeRepository, betRepository, userRepository, ledgerRepository,
            sessionLock, f1DataProvider, fixedClock, transactionTemplate
        );
        sessionKey = SessionKey.of(9472);
        winningDriver = DriverNumber.of(44);

        // Default mock: valid ended session with driver 44
        EventWithDrivers validEndedSession = new EventWithDrivers(
            9472, "Race", "Race", "Circuit", "Country", "XX",
            Instant.parse("2024-06-01T14:00:00Z"), Instant.parse("2024-06-01T16:00:00Z"), 2024,
            List.of(
                new EventWithDrivers.DriverInfo(44, "Lewis Hamilton", "Mercedes", 3),
                new EventWithDrivers.DriverInfo(1, "Max Verstappen", "Red Bull", 2)
            )
        );
        lenient().when(f1DataProvider.getSessionByKey(anyInt(), anyBoolean())).thenReturn(Optional.of(validEndedSession));
    }

    @Nested
    @DisplayName("Event settlement with winning bets")
    class WinningBetsSettlement {

        @Test
        @DisplayName("should mark winning bets as WON and credit user balance")
        void shouldSettleWinningBets() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());

            UserId userId = UserId.of("winner-user");
            User user = User.reconstitute(userId, Money.ofCents(5000), 1L, Instant.now());
            Bet winningBet = Bet.reconstitute(
                UUID.randomUUID(), userId, sessionKey, winningDriver,
                Money.ofCents(1000), Odds.of(3), BetStatus.PENDING, Instant.now(), null
            );

            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of(winningBet));
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            SettleEventResult result = settleEventUseCase.execute(command);

            assertEquals(1, result.totalBets());
            assertEquals(1, result.winningBets());
            assertEquals(3000L, result.totalPayout().toCents());

            verify(eventOutcomeRepository).save(any(EventOutcome.class));
            verify(userRepository).save(any(User.class));
            verify(betRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("should calculate correct payout based on odds")
        void shouldCalculateCorrectPayout() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());

            UserId userId = UserId.of("test-user");
            User user = User.reconstitute(userId, Money.ofCents(0), 1L, Instant.now());
            Bet betWithOdds4 = Bet.reconstitute(
                UUID.randomUUID(), userId, sessionKey, winningDriver,
                Money.ofCents(2500), Odds.of(4), BetStatus.PENDING, Instant.now(), null
            );

            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of(betWithOdds4));
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            SettleEventResult result = settleEventUseCase.execute(command);

            assertEquals(10_000L, result.totalPayout().toCents());
        }
    }

    @Nested
    @DisplayName("Event settlement with losing bets")
    class LosingBetsSettlement {

        @Test
        @DisplayName("should mark losing bets as LOST without crediting balance")
        void shouldSettleLosingBets() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());

            UserId userId = UserId.of("loser-user");
            User user = User.reconstitute(userId, Money.ofCents(5000), 1L, Instant.now());
            DriverNumber losingDriver = DriverNumber.of(1);
            Bet losingBet = Bet.reconstitute(
                UUID.randomUUID(), userId, sessionKey, losingDriver,
                Money.ofCents(1000), Odds.of(2), BetStatus.PENDING, Instant.now(), null
            );

            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of(losingBet));
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            SettleEventResult result = settleEventUseCase.execute(command);

            assertEquals(1, result.totalBets());
            assertEquals(0, result.winningBets());
            assertEquals(0L, result.totalPayout().toCents());
        }
    }

    @Nested
    @DisplayName("Mixed winning and losing bets")
    class MixedBetsSettlement {

        @Test
        @DisplayName("should correctly settle mix of winning and losing bets")
        void shouldSettleMixedBets() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());

            UserId winnerId = UserId.of("winner");
            UserId loserId = UserId.of("loser");
            User winner = User.reconstitute(winnerId, Money.ofCents(0), 1L, Instant.now());
            User loser = User.reconstitute(loserId, Money.ofCents(5000), 1L, Instant.now());

            Bet winningBet = Bet.reconstitute(
                UUID.randomUUID(), winnerId, sessionKey, winningDriver,
                Money.ofCents(1000), Odds.of(2), BetStatus.PENDING, Instant.now(), null
            );
            Bet losingBet = Bet.reconstitute(
                UUID.randomUUID(), loserId, sessionKey, DriverNumber.of(1),
                Money.ofCents(2000), Odds.of(3), BetStatus.PENDING, Instant.now(), null
            );

            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of(winningBet, losingBet));
            when(userRepository.findByIdForUpdate(winnerId)).thenReturn(Optional.of(winner));
            when(userRepository.findByIdForUpdate(loserId)).thenReturn(Optional.of(loser));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            SettleEventResult result = settleEventUseCase.execute(command);

            assertEquals(2, result.totalBets());
            assertEquals(1, result.winningBets());
            assertEquals(2000L, result.totalPayout().toCents());
        }
    }

    @Nested
    @DisplayName("Idempotency checks")
    class IdempotencyChecks {

        @Test
        @DisplayName("should throw EventAlreadySettledException when settled with different winner")
        void shouldRejectSettlementWithDifferentWinner() {
            // Event already settled with driver 1, trying to settle with driver 44
            DriverNumber existingWinner = DriverNumber.of(1);
            EventOutcome existingOutcome = EventOutcome.create(sessionKey, existingWinner);
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.of(existingOutcome));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver); // winningDriver = 44

            assertThrows(EventAlreadySettledException.class, () -> settleEventUseCase.execute(command));

            verify(betRepository, never()).findBySessionKeyAndStatusForUpdate(any(), any());
        }

        @Test
        @DisplayName("should return idempotent success when settled with same winner")
        void shouldReturnIdempotentSuccessForSameWinner() {
            // Event already settled with same winner (44)
            EventOutcome existingOutcome = EventOutcome.create(sessionKey, winningDriver);
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.of(existingOutcome));

            UserId winnerId = UserId.of("idem-winner");
            UserId loserId = UserId.of("idem-loser");
            Bet winningBet = Bet.reconstitute(
                UUID.randomUUID(), winnerId, sessionKey, winningDriver,
                Money.ofCents(1000), Odds.of(3), BetStatus.WON, Instant.now(), Instant.now()
            );
            Bet losingBet = Bet.reconstitute(
                UUID.randomUUID(), loserId, sessionKey, DriverNumber.of(1),
                Money.ofCents(2000), Odds.of(2), BetStatus.LOST, Instant.now(), Instant.now()
            );
            when(betRepository.findBySessionKey(sessionKey)).thenReturn(List.of(winningBet, losingBet));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            SettleEventResult result = settleEventUseCase.execute(command);

            assertEquals(sessionKey.getValue(), result.sessionKey());
            assertEquals(winningDriver.getValue(), result.winningDriverNumber());
            assertEquals(2, result.totalBets());
            assertEquals(1, result.winningBets());
            assertEquals(3000L, result.totalPayout().toCents());

            verify(eventOutcomeRepository, never()).save(any());
            verify(betRepository, never()).findBySessionKeyAndStatusForUpdate(any(), any());
        }
    }

    @Nested
    @DisplayName("Zero bets settlement")
    class ZeroBetsSettlement {

        @Test
        @DisplayName("should successfully settle event with zero pending bets")
        void shouldSettleEventWithZeroBets() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());
            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of());

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            SettleEventResult result = settleEventUseCase.execute(command);

            assertEquals(0, result.totalBets());
            assertEquals(0, result.winningBets());
            assertEquals(0L, result.totalPayout().toCents());

            verify(eventOutcomeRepository).save(any(EventOutcome.class));
            verify(userRepository, never()).findByIdForUpdate(any());
            verify(ledgerRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("should persist event outcome even when no bets exist")
        void shouldPersistOutcomeWithZeroBets() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());
            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of());

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            settleEventUseCase.execute(command);

            ArgumentCaptor<EventOutcome> outcomeCaptor = ArgumentCaptor.forClass(EventOutcome.class);
            verify(eventOutcomeRepository).save(outcomeCaptor.capture());

            EventOutcome savedOutcome = outcomeCaptor.getValue();
            assertEquals(sessionKey, savedOutcome.getSessionKey());
            assertEquals(winningDriver, savedOutcome.getWinningDriverNumber());
        }

        @Test
        @DisplayName("should prevent re-settlement with different winner after zero-bet settlement")
        void shouldPreventReSettlementWithDifferentWinnerAfterZeroBets() {
            // First call - no existing outcome
            when(eventOutcomeRepository.findBySessionKey(sessionKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(EventOutcome.create(sessionKey, winningDriver)));
            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of());

            SettleEventCommand firstCommand = new SettleEventCommand(sessionKey, winningDriver);
            settleEventUseCase.execute(firstCommand);

            // Second call with DIFFERENT winner should fail
            DriverNumber differentWinner = DriverNumber.of(1);
            SettleEventCommand secondCommand = new SettleEventCommand(sessionKey, differentWinner);

            assertThrows(EventAlreadySettledException.class,
                () -> settleEventUseCase.execute(secondCommand));
        }
    }

    @Nested
    @DisplayName("Ledger entry creation")
    class LedgerEntryCreation {

        @Test
        @DisplayName("should create BET_WON ledger entry for winning bets")
        void shouldCreateWonLedgerEntry() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());

            UserId userId = UserId.of("winner");
            User user = User.reconstitute(userId, Money.ofCents(1000), 1L, Instant.now());
            Bet winningBet = Bet.reconstitute(
                UUID.randomUUID(), userId, sessionKey, winningDriver,
                Money.ofCents(500), Odds.of(2), BetStatus.PENDING, Instant.now(), null
            );

            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of(winningBet));
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            settleEventUseCase.execute(command);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LedgerEntry>> ledgerCaptor = ArgumentCaptor.forClass(List.class);
            verify(ledgerRepository).saveAll(ledgerCaptor.capture());

            List<LedgerEntry> entries = ledgerCaptor.getValue();
            assertEquals(1, entries.size());
            assertEquals(LedgerEntryType.BET_WON, entries.get(0).getEntryType());
            assertEquals(1000L, entries.get(0).getAmountCents());
        }

        @Test
        @DisplayName("should create BET_LOST ledger entry for losing bets")
        void shouldCreateLostLedgerEntry() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());

            UserId userId = UserId.of("loser");
            User user = User.reconstitute(userId, Money.ofCents(5000), 1L, Instant.now());
            Bet losingBet = Bet.reconstitute(
                UUID.randomUUID(), userId, sessionKey, DriverNumber.of(1),
                Money.ofCents(1000), Odds.of(2), BetStatus.PENDING, Instant.now(), null
            );

            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of(losingBet));
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            settleEventUseCase.execute(command);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LedgerEntry>> ledgerCaptor = ArgumentCaptor.forClass(List.class);
            verify(ledgerRepository).saveAll(ledgerCaptor.capture());

            List<LedgerEntry> entries = ledgerCaptor.getValue();
            assertEquals(1, entries.size());
            assertEquals(LedgerEntryType.BET_LOST, entries.get(0).getEntryType());
            assertEquals(0L, entries.get(0).getAmountCents());
        }
    }

    @Nested
    @DisplayName("Session and driver validation")
    class SessionAndDriverValidation {

        @Test
        @DisplayName("should throw SessionNotFoundException when session does not exist")
        void shouldThrowWhenSessionNotFound() {
            // HTTP validation happens OUTSIDE transaction - no DB operations should occur
            when(f1DataProvider.getSessionByKey(anyInt(), anyBoolean())).thenReturn(Optional.empty());

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);

            assertThrows(SessionNotFoundException.class, () -> settleEventUseCase.execute(command));

            // Transaction never started, so no DB operations
            verify(eventOutcomeRepository, never()).findBySessionKey(any());
            verify(eventOutcomeRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw EventNotEndedException when event has not ended yet")
        void shouldThrowWhenEventNotEnded() {
            // HTTP validation happens OUTSIDE transaction
            EventWithDrivers futureSession = new EventWithDrivers(
                9472, "Race", "Race", "Circuit", "Country", "XX",
                Instant.parse("2024-06-01T19:00:00Z"), Instant.parse("2024-06-01T21:00:00Z"), 2024,
                List.of(new EventWithDrivers.DriverInfo(44, "Lewis Hamilton", "Mercedes", 3))
            );
            when(f1DataProvider.getSessionByKey(anyInt(), anyBoolean())).thenReturn(Optional.of(futureSession));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);

            assertThrows(EventNotEndedException.class, () -> settleEventUseCase.execute(command));

            // Transaction never started, so no DB operations
            verify(eventOutcomeRepository, never()).findBySessionKey(any());
            verify(eventOutcomeRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw EventNotEndedException when event has no end date")
        void shouldThrowWhenEventHasNoEndDate() {
            // HTTP validation happens OUTSIDE transaction
            EventWithDrivers sessionWithoutEndDate = new EventWithDrivers(
                9472, "Race", "Race", "Circuit", "Country", "XX",
                Instant.parse("2024-06-01T14:00:00Z"), null, 2024,
                List.of(new EventWithDrivers.DriverInfo(44, "Lewis Hamilton", "Mercedes", 3))
            );
            when(f1DataProvider.getSessionByKey(anyInt(), anyBoolean())).thenReturn(Optional.of(sessionWithoutEndDate));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);

            assertThrows(EventNotEndedException.class, () -> settleEventUseCase.execute(command));

            // Transaction never started, so no DB operations
            verify(eventOutcomeRepository, never()).findBySessionKey(any());
            verify(eventOutcomeRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DriverNotInSessionException when winning driver did not participate")
        void shouldThrowWhenDriverNotInSession() {
            // HTTP validation happens OUTSIDE transaction
            EventWithDrivers sessionWithoutDriver = new EventWithDrivers(
                9472, "Race", "Race", "Circuit", "Country", "XX",
                Instant.parse("2024-06-01T14:00:00Z"), Instant.parse("2024-06-01T16:00:00Z"), 2024,
                List.of(new EventWithDrivers.DriverInfo(1, "Max Verstappen", "Red Bull", 2))
            );
            when(f1DataProvider.getSessionByKey(anyInt(), anyBoolean())).thenReturn(Optional.of(sessionWithoutDriver));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);

            assertThrows(DriverNotInSessionException.class, () -> settleEventUseCase.execute(command));

            // Transaction never started, so no DB operations
            verify(eventOutcomeRepository, never()).findBySessionKey(any());
            verify(eventOutcomeRepository, never()).save(any());
        }

        @Test
        @DisplayName("should propagate ExternalServiceUnavailableException when F1 API fails")
        void shouldPropagateExternalServiceException() {
            when(f1DataProvider.getSessionByKey(anyInt(), anyBoolean()))
                .thenThrow(new ExternalServiceUnavailableException("OpenF1", "Service unavailable"));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);

            ExternalServiceUnavailableException ex = assertThrows(
                ExternalServiceUnavailableException.class,
                () -> settleEventUseCase.execute(command)
            );

            assertEquals("OpenF1", ex.getServiceName());

            // Transaction never started, so no DB operations
            verify(eventOutcomeRepository, never()).findBySessionKey(any());
            verify(eventOutcomeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Session lock behavior")
    class SessionLockBehavior {

        @Test
        @DisplayName("should acquire session lock with correct session key")
        void shouldAcquireSessionLockWithCorrectKey() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());
            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of());

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            settleEventUseCase.execute(command);

            verify(sessionLock).acquire(sessionKey);
        }

        @Test
        @DisplayName("should acquire lock before checking existing outcome")
        void shouldAcquireLockBeforeOutcomeCheck() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());
            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of());

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            settleEventUseCase.execute(command);

            // Verify order: lock acquired, then outcome checked
            var inOrder = inOrder(sessionLock, eventOutcomeRepository);
            inOrder.verify(sessionLock).acquire(sessionKey);
            inOrder.verify(eventOutcomeRepository).findBySessionKey(sessionKey);
        }
    }

    @Nested
    @DisplayName("Bet processing order")
    class BetProcessingOrder {

        @Test
        @DisplayName("should process bets sorted by userId to prevent deadlocks")
        void shouldProcessBetsSortedByUserId() {
            when(eventOutcomeRepository.findBySessionKey(sessionKey)).thenReturn(Optional.empty());

            // Create users with userIds that would sort differently than insertion order
            UserId userZ = UserId.of("z-user");
            UserId userA = UserId.of("a-user");
            UserId userM = UserId.of("m-user");

            User zUser = User.reconstitute(userZ, Money.ofCents(5000), 1L, Instant.now());
            User aUser = User.reconstitute(userA, Money.ofCents(5000), 1L, Instant.now());
            User mUser = User.reconstitute(userM, Money.ofCents(5000), 1L, Instant.now());

            // Create bets in non-sorted order (z, a, m)
            Bet betZ = Bet.reconstitute(
                UUID.randomUUID(), userZ, sessionKey, winningDriver,
                Money.ofCents(100), Odds.of(2), BetStatus.PENDING, Instant.now(), null
            );
            Bet betA = Bet.reconstitute(
                UUID.randomUUID(), userA, sessionKey, winningDriver,
                Money.ofCents(100), Odds.of(2), BetStatus.PENDING, Instant.now(), null
            );
            Bet betM = Bet.reconstitute(
                UUID.randomUUID(), userM, sessionKey, winningDriver,
                Money.ofCents(100), Odds.of(2), BetStatus.PENDING, Instant.now(), null
            );

            when(betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING))
                .thenReturn(List.of(betZ, betA, betM));
            when(userRepository.findByIdForUpdate(userZ)).thenReturn(Optional.of(zUser));
            when(userRepository.findByIdForUpdate(userA)).thenReturn(Optional.of(aUser));
            when(userRepository.findByIdForUpdate(userM)).thenReturn(Optional.of(mUser));

            SettleEventCommand command = new SettleEventCommand(sessionKey, winningDriver);
            settleEventUseCase.execute(command);

            // Verify users are fetched in alphabetical order (a, m, z) to prevent deadlocks
            var inOrder = inOrder(userRepository);
            inOrder.verify(userRepository).findByIdForUpdate(userA);
            inOrder.verify(userRepository).findByIdForUpdate(userM);
            inOrder.verify(userRepository).findByIdForUpdate(userZ);
        }
    }
}
