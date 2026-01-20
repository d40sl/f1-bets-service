package com.f1bets.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private static final UserId TEST_USER_ID = UserId.of("user-123");

    @Nested
    @DisplayName("User creation")
    class UserCreation {

        @Test
        @DisplayName("should create new user with initial balance of EUR 100 (10000 cents)")
        void shouldCreateNewUserWithInitialBalance() {
            User user = User.createNew(TEST_USER_ID);
            
            assertEquals(TEST_USER_ID, user.getId());
            assertEquals(10_000L, user.getBalanceCents());
            assertEquals(0L, user.getVersion());
            assertNotNull(user.getCreatedAt());
        }

        @Test
        @DisplayName("should reconstitute user from stored values")
        void shouldReconstituteUserFromStoredValues() {
            Instant createdAt = Instant.now().minusSeconds(3600);
            User user = User.reconstitute(
                TEST_USER_ID,
                Money.ofCents(5000),
                5L,
                createdAt
            );
            
            assertEquals(TEST_USER_ID, user.getId());
            assertEquals(5000L, user.getBalanceCents());
            assertEquals(5L, user.getVersion());
            assertEquals(createdAt, user.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("Balance operations")
    class BalanceOperations {

        @Test
        @DisplayName("should deduct balance successfully")
        void shouldDeductBalanceSuccessfully() {
            User user = User.createNew(TEST_USER_ID);
            Money deductionOf30Euros = Money.ofCents(3000);
            
            user.deductBalance(deductionOf30Euros);
            
            assertEquals(7000L, user.getBalanceCents());
        }

        @Test
        @DisplayName("should throw when deducting more than balance")
        void shouldThrowWhenDeductingMoreThanBalance() {
            User user = User.createNew(TEST_USER_ID);
            Money excessiveDeductionOf200Euros = Money.ofCents(20_000);
            
            assertThrows(IllegalStateException.class, () -> user.deductBalance(excessiveDeductionOf200Euros));
        }

        @Test
        @DisplayName("should throw when deducting zero amount")
        void shouldThrowWhenDeductingZeroAmount() {
            User user = User.createNew(TEST_USER_ID);
            
            assertThrows(IllegalArgumentException.class, () -> user.deductBalance(Money.ZERO));
        }

        @Test
        @DisplayName("should throw when deducting null amount")
        void shouldThrowWhenDeductingNullAmount() {
            User user = User.createNew(TEST_USER_ID);
            
            assertThrows(NullPointerException.class, () -> user.deductBalance(null));
        }

        @Test
        @DisplayName("should add winnings successfully")
        void shouldAddWinningsSuccessfully() {
            User user = User.createNew(TEST_USER_ID);
            Money winningsOf50Euros = Money.ofCents(5000);
            
            user.addWinnings(winningsOf50Euros);
            
            assertEquals(15_000L, user.getBalanceCents());
        }

        @Test
        @DisplayName("should throw when adding zero winnings")
        void shouldThrowWhenAddingZeroWinnings() {
            User user = User.createNew(TEST_USER_ID);
            
            assertThrows(IllegalArgumentException.class, () -> user.addWinnings(Money.ZERO));
        }
    }

    @Nested
    @DisplayName("Affordability check")
    class AffordabilityCheck {

        @Test
        @DisplayName("should return true when user can afford amount")
        void shouldReturnTrueWhenUserCanAffordAmount() {
            User user = User.createNew(TEST_USER_ID);
            
            assertTrue(user.canAfford(Money.ofCents(5000)));
            assertTrue(user.canAfford(Money.ofCents(10_000)));
        }

        @Test
        @DisplayName("should return false when user cannot afford amount")
        void shouldReturnFalseWhenUserCannotAffordAmount() {
            User user = User.createNew(TEST_USER_ID);
            
            assertFalse(user.canAfford(Money.ofCents(10_001)));
            assertFalse(user.canAfford(Money.ofCents(20_000)));
        }
    }

    @Nested
    @DisplayName("Equality and hashing")
    class EqualityAndHashing {

        @Test
        @DisplayName("should be equal when IDs match")
        void shouldBeEqualWhenIdsMatch() {
            User user1 = User.createNew(TEST_USER_ID);
            User user2 = User.reconstitute(TEST_USER_ID, Money.ofCents(5000), 10L, Instant.now());
            
            assertEquals(user1, user2);
            assertEquals(user1.hashCode(), user2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when IDs differ")
        void shouldNotBeEqualWhenIdsDiffer() {
            User user1 = User.createNew(UserId.of("user-1"));
            User user2 = User.createNew(UserId.of("user-2"));
            
            assertNotEquals(user1, user2);
        }
    }

    @Test
    @DisplayName("should have meaningful toString")
    void shouldHaveMeaningfulToString() {
        User user = User.createNew(TEST_USER_ID);
        String str = user.toString();
        
        assertTrue(str.contains("user-123"));
        assertTrue(str.contains("balance"));
    }
}
