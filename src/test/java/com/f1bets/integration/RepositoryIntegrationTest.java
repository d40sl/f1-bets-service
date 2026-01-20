package com.f1bets.integration;

import com.f1bets.domain.model.*;
import com.f1bets.domain.repository.BetRepository;
import com.f1bets.domain.repository.LedgerRepository;
import com.f1bets.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class RepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BetRepository betRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Nested
    @DisplayName("UserRepository tests")
    class UserRepositoryTests {

        @Test
        @DisplayName("should persist and retrieve user")
        void shouldPersistAndRetrieveUser() {
            UserId userId = UserId.of("repo-test-user-" + UUID.randomUUID().toString().substring(0, 8));
            User user = User.createNew(userId);

            userRepository.save(user);
            var retrieved = userRepository.findById(userId);

            assertTrue(retrieved.isPresent());
            assertEquals(userId, retrieved.get().getId());
            assertEquals(10_000L, retrieved.get().getBalanceCents());
        }

        @Test
        @DisplayName("should find user for update with lock")
        void shouldFindUserForUpdateWithLock() {
            UserId userId = UserId.of("lock-test-user-" + UUID.randomUUID().toString().substring(0, 8));
            User user = User.createNew(userId);
            userRepository.save(user);

            var lockedUser = userRepository.findByIdForUpdate(userId);

            assertTrue(lockedUser.isPresent());
            assertEquals(userId, lockedUser.get().getId());
        }

        @Test
        @DisplayName("should return empty when user not found")
        void shouldReturnEmptyWhenUserNotFound() {
            var result = userRepository.findById(UserId.of("nonexistent-user"));
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("BetRepository tests")
    class BetRepositoryTests {

        @Test
        @DisplayName("should persist and retrieve bet")
        void shouldPersistAndRetrieveBet() {
            UserId userId = UserId.of("bet-test-user-" + UUID.randomUUID().toString().substring(0, 8));
            User user = User.createNew(userId);
            userRepository.save(user);

            Bet bet = Bet.place(
                userId,
                SessionKey.of(9472),
                DriverNumber.of(44),
                Money.ofCents(5000),
                Odds.of(3)
            );

            betRepository.save(bet);
            var retrieved = betRepository.findById(bet.getId());

            assertTrue(retrieved.isPresent());
            assertEquals(bet.getId(), retrieved.get().getId());
            assertEquals(BetStatus.PENDING, retrieved.get().getStatus());
        }

        @Test
        @DisplayName("should find bets by session key and status")
        void shouldFindBetsBySessionKeyAndStatus() {
            UserId userId = UserId.of("multi-bet-user-" + UUID.randomUUID().toString().substring(0, 8));
            User user = User.createNew(userId);
            userRepository.save(user);

            SessionKey sessionKey = SessionKey.of(99990 + (int)(System.nanoTime() % 9000));

            Bet bet1 = Bet.place(userId, sessionKey, DriverNumber.of(1), Money.ofCents(1000), Odds.of(2));
            Bet bet2 = Bet.place(userId, sessionKey, DriverNumber.of(44), Money.ofCents(2000), Odds.of(3));
            betRepository.save(bet1);
            betRepository.save(bet2);

            List<Bet> pendingBets = betRepository.findBySessionKeyAndStatusForUpdate(sessionKey, BetStatus.PENDING);

            assertEquals(2, pendingBets.size());
        }
    }

    @Nested
    @DisplayName("LedgerRepository tests")
    class LedgerRepositoryTests {

        @Test
        @DisplayName("should persist ledger entry")
        void shouldPersistLedgerEntry() {
            UserId userId = UserId.of("ledger-user-" + UUID.randomUUID().toString().substring(0, 8));
            User user = User.createNew(userId);
            userRepository.save(user);

            LedgerEntry entry = LedgerEntry.initialCredit(userId, 10_000L);
            ledgerRepository.save(entry);

            var entries = ledgerRepository.findByUserId(userId);
            assertEquals(1, entries.size());
            assertEquals(LedgerEntryType.INITIAL_CREDIT, entries.get(0).getEntryType());
        }

        @Test
        @DisplayName("should find ledger entries by user ID")
        void shouldFindLedgerEntriesByUserId() {
            UserId userId = UserId.of("ledger-multi-" + UUID.randomUUID().toString().substring(0, 8));
            User user = User.createNew(userId);
            userRepository.save(user);

            Bet bet = Bet.place(userId, SessionKey.of(1234), DriverNumber.of(1), Money.ofCents(1000), Odds.of(2));
            betRepository.save(bet);

            LedgerEntry credit = LedgerEntry.initialCredit(userId, 10_000L);
            LedgerEntry betPlaced = LedgerEntry.betPlaced(userId, 1000L, 9000L, bet.getId());

            ledgerRepository.save(credit);
            ledgerRepository.save(betPlaced);

            var entries = ledgerRepository.findByUserId(userId);
            assertEquals(2, entries.size());
        }
    }
}
