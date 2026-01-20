package com.f1bets.application.service;

import com.f1bets.domain.model.Odds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OddsCalculator to ensure odds are deterministic and consistent.
 *
 * CRITICAL INVARIANT: Users see odds when listing events and expect the same
 * odds when placing a bet. Any drift between listing and betting creates
 * disputes and erodes trust.
 */
@DisplayName("OddsCalculator")
class OddsCalculatorTest {

    private static final String TEST_SEED = "TEST_SEED";
    private final OddsCalculator calculator = new OddsCalculator(TEST_SEED);

    @Nested
    @DisplayName("Deterministic odds invariant")
    class DeterministicOddsInvariant {

        @Test
        @DisplayName("odds for same session+driver should be identical across multiple calls")
        void oddsShouldBeStableForSameInputs() {
            int sessionKey = 9158;
            int driverNumber = 44;

            Odds first = calculator.calculate(sessionKey, driverNumber);
            Odds second = calculator.calculate(sessionKey, driverNumber);
            Odds third = calculator.calculate(sessionKey, driverNumber);

            assertAll(
                () -> assertEquals(first, second, "Odds must be deterministic across calls"),
                () -> assertEquals(second, third, "Odds must remain stable"),
                () -> assertEquals(first.getValue(), third.getValue(), "Numeric value must match")
            );
        }

        @Test
        @DisplayName("odds shown at listing time must equal odds at bet placement time")
        void oddsAtListingMustEqualOddsAtBetting() {
            // Simulate: user views event listing
            int sessionKey = 9472;
            int driverNumber = 1;
            Odds oddsAtListing = calculator.calculate(sessionKey, driverNumber);

            // Simulate: some time passes, user places bet
            // (In a real scenario, this could be seconds or minutes later)
            Odds oddsAtBetting = calculator.calculate(sessionKey, driverNumber);

            assertEquals(oddsAtListing, oddsAtBetting,
                "CRITICAL: Odds drift between listing and betting would cause disputes");
        }

        @Test
        @DisplayName("different drivers in same session should potentially have different odds")
        void differentDriversShouldHavePotentiallyDifferentOdds() {
            int sessionKey = 9158;
            Set<Integer> uniqueOdds = new HashSet<>();

            // Calculate odds for multiple drivers
            for (int driverNumber = 1; driverNumber <= 20; driverNumber++) {
                Odds odds = calculator.calculate(sessionKey, driverNumber);
                uniqueOdds.add(odds.getValue());
            }

            // With 20 drivers and 3 possible odds values, we should see variety
            assertTrue(uniqueOdds.size() > 1,
                "Odds should vary across different drivers (got only: " + uniqueOdds + ")");
        }

        @Test
        @DisplayName("same driver in different sessions should potentially have different odds")
        void sameDriverDifferentSessionsShouldVary() {
            int driverNumber = 44; // Hamilton
            Set<Integer> uniqueOdds = new HashSet<>();

            // Calculate odds across multiple sessions
            for (int sessionKey = 9000; sessionKey < 9020; sessionKey++) {
                Odds odds = calculator.calculate(sessionKey, driverNumber);
                uniqueOdds.add(odds.getValue());
            }

            assertTrue(uniqueOdds.size() > 1,
                "Odds should vary across different sessions for same driver");
        }
    }

    @Nested
    @DisplayName("Odds value range validation")
    class OddsValueRange {

        @RepeatedTest(100)
        @DisplayName("odds should always be 2, 3, or 4 for random inputs")
        void oddsShouldAlwaysBeInValidRange() {
            int sessionKey = (int) (Math.random() * 100000);
            int driverNumber = (int) (Math.random() * 99) + 1;

            Odds odds = calculator.calculate(sessionKey, driverNumber);

            assertTrue(odds.getValue() >= 2 && odds.getValue() <= 4,
                "Odds must be 2, 3, or 4 but was " + odds.getValue() +
                " for session=" + sessionKey + ", driver=" + driverNumber);
        }

        @Test
        @DisplayName("odds distribution should cover all valid values")
        void oddsDistributionShouldCoverAllValues() {
            Set<Integer> seenOdds = new HashSet<>();

            // Generate enough samples to statistically cover all values
            for (int i = 0; i < 1000; i++) {
                Odds odds = calculator.calculate(i, i % 99 + 1);
                seenOdds.add(odds.getValue());
            }

            assertEquals(Set.of(2, 3, 4), seenOdds,
                "All valid odds values (2, 3, 4) should appear in distribution");
        }
    }

    @Nested
    @DisplayName("Seed behavior")
    class SeedBehavior {

        @Test
        @DisplayName("different seeds should produce different odds for same inputs")
        void differentSeedsShouldProduceDifferentOdds() {
            OddsCalculator calc1 = new OddsCalculator("SEED_A");
            OddsCalculator calc2 = new OddsCalculator("SEED_B");

            int matchCount = 0;
            int totalTests = 100;

            for (int i = 0; i < totalTests; i++) {
                Odds odds1 = calc1.calculate(i, 44);
                Odds odds2 = calc2.calculate(i, 44);
                if (odds1.equals(odds2)) {
                    matchCount++;
                }
            }

            // With different seeds, we expect significant divergence
            // (not all matching, allowing for some collision)
            assertTrue(matchCount < totalTests * 0.9,
                "Different seeds should produce different odds distribution");
        }

        @Test
        @DisplayName("same seed should always produce identical results")
        void sameSeedShouldProduceIdenticalResults() {
            OddsCalculator calc1 = new OddsCalculator("IDENTICAL_SEED");
            OddsCalculator calc2 = new OddsCalculator("IDENTICAL_SEED");

            for (int sessionKey = 9000; sessionKey < 9100; sessionKey++) {
                for (int driverNumber = 1; driverNumber <= 20; driverNumber++) {
                    Odds odds1 = calc1.calculate(sessionKey, driverNumber);
                    Odds odds2 = calc2.calculate(sessionKey, driverNumber);

                    assertEquals(odds1, odds2,
                        "Same seed must produce identical odds for session=" +
                        sessionKey + ", driver=" + driverNumber);
                }
            }
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle session key of 0")
        void shouldHandleSessionKeyZero() {
            Odds odds = calculator.calculate(0, 1);
            assertTrue(odds.getValue() >= 2 && odds.getValue() <= 4);
        }

        @Test
        @DisplayName("should handle maximum driver number (99)")
        void shouldHandleMaxDriverNumber() {
            Odds odds = calculator.calculate(9158, 99);
            assertTrue(odds.getValue() >= 2 && odds.getValue() <= 4);
        }

        @Test
        @DisplayName("should handle large session key")
        void shouldHandleLargeSessionKey() {
            Odds odds = calculator.calculate(Integer.MAX_VALUE, 44);
            assertTrue(odds.getValue() >= 2 && odds.getValue() <= 4);
        }

        @Test
        @DisplayName("should handle negative session key gracefully")
        void shouldHandleNegativeSessionKey() {
            // While not valid in production, the calculator should not crash
            Odds odds = calculator.calculate(-1, 44);
            assertTrue(odds.getValue() >= 2 && odds.getValue() <= 4);
        }
    }
}
