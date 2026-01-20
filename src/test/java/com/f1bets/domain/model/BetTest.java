package com.f1bets.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BetTest {

    private UserId userId;
    private SessionKey sessionKey;
    private DriverNumber driverNumber;
    private Money stake;
    private Odds odds;

    @BeforeEach
    void setUp() {
        userId = UserId.of("user-123");
        sessionKey = SessionKey.of(9472);
        driverNumber = DriverNumber.of(44);
        stake = Money.ofCents(5000);
        odds = Odds.of(3);
    }

    @Nested
    @DisplayName("Bet creation")
    class BetCreation {

        @Test
        @DisplayName("should create bet with PENDING status")
        void shouldCreateBetWithPendingStatus() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            
            assertNotNull(bet.getId());
            assertEquals(userId, bet.getUserId());
            assertEquals(sessionKey, bet.getSessionKey());
            assertEquals(driverNumber, bet.getDriverNumber());
            assertEquals(stake, bet.getStake());
            assertEquals(odds, bet.getOdds());
            assertEquals(BetStatus.PENDING, bet.getStatus());
            assertTrue(bet.isPending());
            assertNotNull(bet.getCreatedAt());
            assertNull(bet.getSettledAt());
        }

        @Test
        @DisplayName("should reconstitute bet from stored values")
        void shouldReconstituteBetFromStoredValues() {
            UUID id = UUID.randomUUID();
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant settledAt = Instant.now();
            
            Bet bet = Bet.reconstitute(
                id, userId, sessionKey, driverNumber,
                stake, odds, BetStatus.WON, createdAt, settledAt
            );
            
            assertEquals(id, bet.getId());
            assertEquals(BetStatus.WON, bet.getStatus());
            assertEquals(createdAt, bet.getCreatedAt());
            assertEquals(settledAt, bet.getSettledAt());
        }
    }

    @Nested
    @DisplayName("Bet settlement")
    class BetSettlement {

        @Test
        @DisplayName("should mark bet as won")
        void shouldMarkBetAsWon() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            
            bet.markAsWon();
            
            assertEquals(BetStatus.WON, bet.getStatus());
            assertTrue(bet.isWon());
            assertFalse(bet.isPending());
            assertNotNull(bet.getSettledAt());
        }

        @Test
        @DisplayName("should mark bet as lost")
        void shouldMarkBetAsLost() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            
            bet.markAsLost();
            
            assertEquals(BetStatus.LOST, bet.getStatus());
            assertFalse(bet.isWon());
            assertFalse(bet.isPending());
            assertNotNull(bet.getSettledAt());
        }

        @Test
        @DisplayName("should throw when marking already won bet as won")
        void shouldThrowWhenMarkingAlreadyWonBetAsWon() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            bet.markAsWon();
            
            assertThrows(IllegalStateException.class, bet::markAsWon);
        }

        @Test
        @DisplayName("should throw when marking already won bet as lost")
        void shouldThrowWhenMarkingAlreadyWonBetAsLost() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            bet.markAsWon();
            
            assertThrows(IllegalStateException.class, bet::markAsLost);
        }

        @Test
        @DisplayName("should throw when marking already lost bet as won")
        void shouldThrowWhenMarkingAlreadyLostBetAsWon() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            bet.markAsLost();
            
            assertThrows(IllegalStateException.class, bet::markAsWon);
        }

        @Test
        @DisplayName("should throw when marking already lost bet as lost")
        void shouldThrowWhenMarkingAlreadyLostBetAsLost() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            bet.markAsLost();
            
            assertThrows(IllegalStateException.class, bet::markAsLost);
        }
    }

    @Nested
    @DisplayName("Payout calculation")
    class PayoutCalculation {

        @Test
        @DisplayName("should calculate payout as stake multiplied by odds")
        void shouldCalculatePayoutCorrectly() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            
            Money payout = bet.calculatePayout();
            
            assertEquals(15_000, payout.toCents());
        }
    }

    @Nested
    @DisplayName("Driver matching")
    class DriverMatching {

        @Test
        @DisplayName("should return true when driver matches")
        void shouldReturnTrueWhenDriverMatches() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            
            assertTrue(bet.isForDriver(DriverNumber.of(44)));
        }

        @Test
        @DisplayName("should return false when driver does not match")
        void shouldReturnFalseWhenDriverDoesNotMatch() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            
            assertFalse(bet.isForDriver(DriverNumber.of(1)));
        }
    }

    @Nested
    @DisplayName("Value accessors")
    class ValueAccessors {

        @Test
        @DisplayName("should return stake in cents")
        void shouldReturnStakeInCents() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            assertEquals(5000, bet.getStakeCents());
        }

        @Test
        @DisplayName("should return odds value")
        void shouldReturnOddsValue() {
            Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            assertEquals(3, bet.getOddsValue());
        }
    }

    @Nested
    @DisplayName("Equality and hashing")
    class EqualityAndHashing {

        @Test
        @DisplayName("should be equal when IDs match")
        void shouldBeEqualWhenIdsMatch() {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            
            Bet bet1 = Bet.reconstitute(id, userId, sessionKey, driverNumber, stake, odds, BetStatus.PENDING, now, null);
            Bet bet2 = Bet.reconstitute(id, userId, sessionKey, driverNumber, stake, odds, BetStatus.WON, now, now);
            
            assertEquals(bet1, bet2);
            assertEquals(bet1.hashCode(), bet2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when IDs differ (different UUIDs)")
        void shouldNotBeEqualWhenIdsDiffer() {
            Bet bet1 = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            Bet bet2 = Bet.place(userId, sessionKey, driverNumber, stake, odds);
            
            assertNotEquals(bet1, bet2);
        }
    }

    @Test
    @DisplayName("should have meaningful toString with sessionKey, driverNumber, and status")
    void shouldHaveMeaningfulToString() {
        Bet bet = Bet.place(userId, sessionKey, driverNumber, stake, odds);
        String str = bet.toString();
        
        assertTrue(str.contains("9472"));
        assertTrue(str.contains("44"));
        assertTrue(str.contains("PENDING"));
    }
}
