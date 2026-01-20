package com.f1bets.application.usecase;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.PlaceBetCommand;
import com.f1bets.application.dto.PlaceBetResult;
import com.f1bets.application.port.F1DataProvider;
import com.f1bets.application.port.SessionLock;
import com.f1bets.application.service.OddsCalculator;
import com.f1bets.domain.exception.DriverNotInSessionException;
import com.f1bets.domain.exception.EventAlreadySettledException;
import com.f1bets.domain.exception.ExternalServiceUnavailableException;
import com.f1bets.domain.exception.InsufficientBalanceException;
import com.f1bets.domain.exception.SessionNotFoundException;
import com.f1bets.domain.model.Bet;
import com.f1bets.domain.model.BetStatus;
import com.f1bets.domain.model.DriverNumber;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceBetUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BetRepository betRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private EventOutcomeRepository eventOutcomeRepository;

    @Mock
    private OddsCalculator oddsCalculator;

    @Mock
    private F1DataProvider f1DataProvider;

    @Mock
    private SessionLock sessionLock;

    @Mock
    private TransactionTemplate transactionTemplate;

    private PlaceBetUseCase placeBetUseCase;

    private UserId userId;
    private SessionKey sessionKey;
    private DriverNumber driverNumber;
    private Money stake;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Configure transactionTemplate to execute callbacks synchronously
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
            .thenAnswer(invocation -> {
                TransactionCallback<PlaceBetResult> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });

        placeBetUseCase = new PlaceBetUseCase(
            userRepository, betRepository, ledgerRepository, eventOutcomeRepository,
            oddsCalculator, f1DataProvider, sessionLock, transactionTemplate
        );
        userId = UserId.of("test-user");
        sessionKey = SessionKey.of(9472);
        driverNumber = DriverNumber.of(44);
        stake = Money.ofCents(5000);

        lenient().when(oddsCalculator.calculate(anyInt(), anyInt())).thenReturn(Odds.of(3));

        // Default mock: valid session with driver 44
        EventWithDrivers validSession = new EventWithDrivers(
            9472, "Race", "Race", "Circuit", "Country", "XX",
            Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 2024,
            List.of(new EventWithDrivers.DriverInfo(44, "Lewis Hamilton", "Mercedes", 3))
        );
        lenient().when(f1DataProvider.getSessionByKey(anyInt())).thenReturn(Optional.of(validSession));
    }

    @Nested
    @DisplayName("Bet placement for existing user")
    class ExistingUserBetPlacement {

        @Test
        @DisplayName("should place bet and deduct balance for existing user")
        void shouldPlaceBetForExistingUser() {
            User existingUser = User.reconstitute(userId, Money.ofCents(10_000), 1L, Instant.now());
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(existingUser));

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);
            PlaceBetResult result = placeBetUseCase.execute(command);

            assertNotNull(result.betId());
            assertEquals("PENDING", result.status());
            assertEquals(5000, result.userBalance().toCents());

            verify(userRepository).save(any(User.class));
            verify(betRepository).save(any(Bet.class));
            verify(ledgerRepository).save(any(LedgerEntry.class));
        }

        @Test
        @DisplayName("should compute server-derived odds based on session and driver")
        void shouldComputeServerDerivedOdds() {
            User existingUser = User.reconstitute(userId, Money.ofCents(10_000), 1L, Instant.now());
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(existingUser));

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);
            PlaceBetResult result = placeBetUseCase.execute(command);

            int odds = result.odds();
            assertTrue(odds >= 2 && odds <= 4, "Odds should be 2, 3, or 4");

            Money expectedPayout = Money.ofCents(stake.toCents() * odds);
            assertEquals(expectedPayout.toCents(), result.potentialWinnings().toCents());
        }

        @Test
        @DisplayName("should throw InsufficientBalanceException when balance is too low")
        void shouldThrowWhenBalanceInsufficient() {
            User poorUser = User.reconstitute(userId, Money.ofCents(1000), 1L, Instant.now());
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(poorUser));

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);

            assertThrows(InsufficientBalanceException.class, () -> placeBetUseCase.execute(command));

            verify(userRepository, never()).save(any());
            verify(betRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Bet placement for new user")
    class NewUserBetPlacement {

        @Test
        @DisplayName("should create new user with initial balance when user does not exist")
        void shouldCreateNewUserWhenNotExists() {
            when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(User.reconstitute(userId, Money.ofCents(10_000), 0L, Instant.now())));
            when(userRepository.insertIfAbsent(any(User.class))).thenReturn(true);

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);
            PlaceBetResult result = placeBetUseCase.execute(command);

            assertNotNull(result.betId());
            assertEquals(5000, result.userBalance().toCents());

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertEquals(5_000L, savedUser.getBalanceCents());
        }

        @Test
        @DisplayName("should record initial credit in ledger for new user")
        void shouldRecordInitialCreditForNewUser() {
            when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(User.reconstitute(userId, Money.ofCents(10_000), 0L, Instant.now())));
            when(userRepository.insertIfAbsent(any(User.class))).thenReturn(true);

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);
            placeBetUseCase.execute(command);

            ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(ledgerRepository, times(2)).save(ledgerCaptor.capture());

            LedgerEntry initialCredit = ledgerCaptor.getAllValues().get(0);
            assertEquals(LedgerEntryType.INITIAL_CREDIT, initialCredit.getEntryType());
            assertEquals(10_000L, initialCredit.getAmountCents());
        }

        @Test
        @DisplayName("should handle concurrent user creation when insertIfAbsent returns false")
        void shouldHandleConcurrentUserCreation() {
            // First findByIdForUpdate returns empty (user doesn't exist)
            // insertIfAbsent returns false (another request created user concurrently)
            // Second findByIdForUpdate returns the user created by concurrent request
            User concurrentlyCreatedUser = User.reconstitute(userId, Money.ofCents(10_000), 0L, Instant.now());
            when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrentlyCreatedUser));
            when(userRepository.insertIfAbsent(any(User.class))).thenReturn(false);

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);
            PlaceBetResult result = placeBetUseCase.execute(command);

            assertNotNull(result.betId());
            assertEquals(5000, result.userBalance().toCents());

            // No initial credit should be recorded since we didn't create the user
            ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(ledgerRepository, times(1)).save(ledgerCaptor.capture());

            LedgerEntry entry = ledgerCaptor.getValue();
            assertEquals(LedgerEntryType.BET_PLACED, entry.getEntryType());
        }
    }

    @Nested
    @DisplayName("Ledger entry creation")
    class LedgerEntryCreation {

        @Test
        @DisplayName("should create BET_PLACED ledger entry with negative amount")
        void shouldCreateBetPlacedLedgerEntry() {
            User existingUser = User.reconstitute(userId, Money.ofCents(10_000), 1L, Instant.now());
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(existingUser));

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);
            placeBetUseCase.execute(command);

            ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(ledgerRepository).save(ledgerCaptor.capture());

            LedgerEntry entry = ledgerCaptor.getValue();
            assertEquals(LedgerEntryType.BET_PLACED, entry.getEntryType());
            assertEquals(-5000L, entry.getAmountCents());
            assertEquals(5000L, entry.getBalanceAfterCents());
        }
    }

    @Nested
    @DisplayName("Settled event validation")
    class SettledEventValidation {

        @Test
        @DisplayName("should throw EventAlreadySettledException when event is already settled")
        void shouldThrowWhenEventAlreadySettled() {
            // HTTP validation happens OUTSIDE transaction, settlement check happens INSIDE
            when(eventOutcomeRepository.existsBySessionKey(sessionKey)).thenReturn(true);

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);

            assertThrows(EventAlreadySettledException.class, () -> placeBetUseCase.execute(command));

            // HTTP validation happens first (outside transaction)
            verify(f1DataProvider).getSessionByKey(anyInt());
            // Settlement check prevents further processing
            verify(userRepository, never()).findByIdForUpdate(any());
            verify(betRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Idempotency handling")
    class IdempotencyHandling {

        @Test
        @DisplayName("should return existing bet when idempotency key is reused")
        void shouldReturnExistingBetForIdempotencyKey() {
            String idempotencyKey = "550e8400-e29b-41d4-a716-446655440000";
            Bet existingBet = Bet.reconstitute(
                UUID.randomUUID(), userId, sessionKey, driverNumber,
                stake, Odds.of(2), BetStatus.PENDING, Instant.now(), null, idempotencyKey
            );

            when(betRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingBet));
            when(ledgerRepository.findBalanceAfterForBet(existingBet.getId()))
                .thenReturn(Optional.of(Money.ofCents(7500)));

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake, idempotencyKey);
            PlaceBetResult result = placeBetUseCase.execute(command);

            assertEquals(existingBet.getId(), result.betId());
            assertEquals(7500L, result.userBalance().toCents());

            verify(eventOutcomeRepository, never()).existsBySessionKey(any());
            verify(userRepository, never()).save(any());
            verify(betRepository, never()).save(any());
            verify(ledgerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Session and driver validation")
    class SessionAndDriverValidation {

        @Test
        @DisplayName("should throw SessionNotFoundException when session does not exist")
        void shouldThrowWhenSessionNotFound() {
            // Session validation happens OUTSIDE transaction, before any DB checks
            when(f1DataProvider.getSessionByKey(anyInt())).thenReturn(Optional.empty());

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);

            assertThrows(SessionNotFoundException.class, () -> placeBetUseCase.execute(command));

            // Transaction never started, so no DB operations
            verify(eventOutcomeRepository, never()).existsBySessionKey(any());
            verify(betRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DriverNotInSessionException when driver did not participate")
        void shouldThrowWhenDriverNotInSession() {
            // Session validation happens OUTSIDE transaction
            EventWithDrivers sessionWithoutDriver = new EventWithDrivers(
                9472, "Race", "Race", "Circuit", "Country", "XX",
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 2024,
                List.of(new EventWithDrivers.DriverInfo(1, "Max Verstappen", "Red Bull", 2))
            );
            when(f1DataProvider.getSessionByKey(anyInt())).thenReturn(Optional.of(sessionWithoutDriver));

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);

            assertThrows(DriverNotInSessionException.class, () -> placeBetUseCase.execute(command));

            // Transaction never started, so no DB operations
            verify(eventOutcomeRepository, never()).existsBySessionKey(any());
            verify(betRepository, never()).save(any());
        }

        @Test
        @DisplayName("should propagate ExternalServiceUnavailableException when F1 API fails")
        void shouldPropagateExternalServiceException() {
            when(f1DataProvider.getSessionByKey(anyInt()))
                .thenThrow(new ExternalServiceUnavailableException("OpenF1", "Service unavailable"));

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);

            ExternalServiceUnavailableException ex = assertThrows(
                ExternalServiceUnavailableException.class,
                () -> placeBetUseCase.execute(command)
            );

            assertEquals("OpenF1", ex.getServiceName());

            // Transaction never started, so no DB operations
            verify(eventOutcomeRepository, never()).existsBySessionKey(any());
            verify(betRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Session lock behavior")
    class SessionLockBehavior {

        @Test
        @DisplayName("should acquire session lock with correct session key")
        void shouldAcquireSessionLockWithCorrectKey() {
            User existingUser = User.reconstitute(userId, Money.ofCents(10_000), 1L, Instant.now());
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(existingUser));

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);
            placeBetUseCase.execute(command);

            verify(sessionLock).acquire(sessionKey);
        }

        @Test
        @DisplayName("should acquire lock before checking settlement status")
        void shouldAcquireLockBeforeSettlementCheck() {
            User existingUser = User.reconstitute(userId, Money.ofCents(10_000), 1L, Instant.now());
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(existingUser));

            PlaceBetCommand command = new PlaceBetCommand(userId, sessionKey, driverNumber, stake);
            placeBetUseCase.execute(command);

            // Verify order: lock acquired, then settlement checked
            var inOrder = inOrder(sessionLock, eventOutcomeRepository);
            inOrder.verify(sessionLock).acquire(sessionKey);
            inOrder.verify(eventOutcomeRepository).existsBySessionKey(sessionKey);
        }
    }
}
