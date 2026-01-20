package com.f1bets.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OddsTest {

    @Nested
    @DisplayName("Creation from value")
    class CreationFromValue {

        @ParameterizedTest(name = "should accept odds value {0}")
        @ValueSource(ints = {2, 3, 4})
        @DisplayName("should accept valid odds values")
        void shouldAcceptValidOddsValues(int value) {
            Odds odds = Odds.of(value);
            assertEquals(value, odds.getValue());
        }

        @ParameterizedTest(name = "should reject odds value {0}")
        @ValueSource(ints = {0, 1, 5, 10, -1, 100})
        @DisplayName("should reject invalid odds values")
        void shouldRejectInvalidOddsValues(int value) {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Odds.of(value)
            );
            assertTrue(exception.getMessage().contains("Invalid odds value"));
        }
    }

    @Nested
    @DisplayName("Deterministic odds generation")
    class DeterministicOddsGeneration {

        private static final String TEST_SEED = "TEST_SEED";

        @Test
        @DisplayName("should generate same odds for same session, driver, and seed")
        void shouldGenerateSameOddsForSameInputs() {
            Odds odds1 = Odds.fromSessionAndDriver(9472, 1, TEST_SEED);
            Odds odds2 = Odds.fromSessionAndDriver(9472, 1, TEST_SEED);

            assertEquals(odds1.getValue(), odds2.getValue());
        }

        @Test
        @DisplayName("should generate valid odds for different drivers in same session")
        void shouldGenerateValidOddsForDifferentDriversInSameSession() {
            int sessionKey = 9472;

            Odds odds1 = Odds.fromSessionAndDriver(sessionKey, 1, TEST_SEED);
            Odds odds44 = Odds.fromSessionAndDriver(sessionKey, 44, TEST_SEED);

            assertTrue(odds1.getValue() >= 2 && odds1.getValue() <= 4);
            assertTrue(odds44.getValue() >= 2 && odds44.getValue() <= 4);
        }

        @Test
        @DisplayName("should always produce valid odds (2, 3, or 4) for any input combination")
        void shouldAlwaysProduceValidOdds() {
            for (int session = 1; session <= 100; session++) {
                for (int driver = 1; driver <= 99; driver++) {
                    Odds odds = Odds.fromSessionAndDriver(session, driver, TEST_SEED);
                    int value = odds.getValue();
                    assertTrue(value >= 2 && value <= 4,
                        "Invalid odds " + value + " for session=" + session + ", driver=" + driver);
                }
            }
        }

        @Test
        @DisplayName("should generate different odds for different seeds")
        void shouldGenerateDifferentOddsForDifferentSeeds() {
            // With different seeds, we expect at least some odds to differ
            int differentCount = 0;
            for (int session = 1; session <= 100; session++) {
                Odds odds1 = Odds.fromSessionAndDriver(session, 1, "SEED_A");
                Odds odds2 = Odds.fromSessionAndDriver(session, 1, "SEED_B");
                if (odds1.getValue() != odds2.getValue()) {
                    differentCount++;
                }
            }
            assertTrue(differentCount > 0, "Different seeds should produce different odds for at least some inputs");
        }
    }

    @Nested
    @DisplayName("Payout calculation")
    class PayoutCalculation {

        @Test
        @DisplayName("should calculate payout with odds 2")
        void shouldCalculatePayoutWithOdds2() {
            Odds odds = Odds.of(2);
            Money stake = Money.ofCents(1000);
            Money payout = odds.calculatePayout(stake);
            assertEquals(2000, payout.toCents());
        }

        @Test
        @DisplayName("should calculate payout with odds 3")
        void shouldCalculatePayoutWithOdds3() {
            Odds odds = Odds.of(3);
            Money stake = Money.ofCents(1000);
            Money payout = odds.calculatePayout(stake);
            assertEquals(3000, payout.toCents());
        }

        @Test
        @DisplayName("should calculate payout with odds 4")
        void shouldCalculatePayoutWithOdds4() {
            Odds odds = Odds.of(4);
            Money stake = Money.ofCents(1000);
            Money payout = odds.calculatePayout(stake);
            assertEquals(4000, payout.toCents());
        }

        @Test
        @DisplayName("should reject null stake")
        void shouldRejectNullStake() {
            Odds odds = Odds.of(2);
            assertThrows(NullPointerException.class, () -> odds.calculatePayout(null));
        }
    }

    @Nested
    @DisplayName("Equality and hashing")
    class EqualityAndHashing {

        @Test
        @DisplayName("should be equal for same value")
        void shouldBeEqualForSameValue() {
            Odds a = Odds.of(3);
            Odds b = Odds.of(3);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different values")
        void shouldNotBeEqualForDifferentValues() {
            Odds a = Odds.of(2);
            Odds b = Odds.of(4);
            assertNotEquals(a, b);
        }
    }

    @Test
    @DisplayName("should have meaningful toString")
    void shouldHaveMeaningfulToString() {
        Odds odds = Odds.of(3);
        assertEquals("3", odds.toString());
    }
}
