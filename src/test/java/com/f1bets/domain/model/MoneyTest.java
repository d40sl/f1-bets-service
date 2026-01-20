package com.f1bets.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Nested
    @DisplayName("Creation from cents")
    class CreationFromCents {

        @Test
        @DisplayName("should create Money from positive cents")
        void shouldCreateMoneyFromPositiveCents() {
            Money money = Money.ofCents(2500);
            assertEquals(2500, money.toCents());
        }

        @Test
        @DisplayName("should create Money from zero cents")
        void shouldCreateMoneyFromZeroCents() {
            Money money = Money.ofCents(0);
            assertEquals(0, money.toCents());
            assertTrue(money.isZero());
        }

        @Test
        @DisplayName("should reject negative cents")
        void shouldRejectNegativeCents() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Money.ofCents(-100)
            );
            assertTrue(exception.getMessage().contains("cannot be negative"));
        }
    }

    @Nested
    @DisplayName("Creation for stakes")
    class CreationForStakes {

        @Test
        @DisplayName("should create stake from positive cents")
        void shouldCreateStakeFromPositiveCents() {
            Money stake = Money.forStake(5000);
            assertEquals(5000, stake.toCents());
        }

        @Test
        @DisplayName("should reject zero stake")
        void shouldRejectZeroStake() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Money.forStake(0)
            );
            assertTrue(exception.getMessage().contains("must be positive"));
        }

        @Test
        @DisplayName("should reject negative stake")
        void shouldRejectNegativeStake() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Money.forStake(-100)
            );
            assertTrue(exception.getMessage().contains("must be positive"));
        }

        @Test
        @DisplayName("should reject stake exceeding maximum")
        void shouldRejectStakeExceedingMaximum() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Money.forStake(Money.MAX_STAKE_CENTS + 1)
            );
            assertTrue(exception.getMessage().contains("exceeds maximum"));
        }

        @Test
        @DisplayName("should accept stake at maximum limit")
        void shouldAcceptStakeAtMaximumLimit() {
            Money stake = Money.forStake(Money.MAX_STAKE_CENTS);
            assertEquals(Money.MAX_STAKE_CENTS, stake.toCents());
        }
    }

    @Nested
    @DisplayName("Conversion from BigDecimal")
    class ConversionFromBigDecimal {

        @Test
        @DisplayName("should convert EUR 25.00 to 2500 cents")
        void shouldConvertDecimalToCents() {
            Money money = Money.fromDecimal(new BigDecimal("25.00"));
            assertEquals(2500, money.toCents());
        }

        @Test
        @DisplayName("should convert EUR 0.01 to 1 cent")
        void shouldConvertMinimalDecimal() {
            Money money = Money.fromDecimal(new BigDecimal("0.01"));
            assertEquals(1, money.toCents());
        }

        @Test
        @DisplayName("should reject more than 2 decimal places")
        void shouldRejectMoreThanTwoDecimalPlaces() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Money.fromDecimal(new BigDecimal("10.001"))
            );
            assertTrue(exception.getMessage().contains("more than 2 decimal places"));
        }

        @Test
        @DisplayName("should reject null amount")
        void shouldRejectNullAmount() {
            assertThrows(IllegalArgumentException.class, () -> Money.fromDecimal(null));
        }

        @Test
        @DisplayName("should reject negative amount")
        void shouldRejectNegativeAmount() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Money.fromDecimal(new BigDecimal("-10.00"))
            );
            assertTrue(exception.getMessage().contains("cannot be negative"));
        }
    }

    @Nested
    @DisplayName("Arithmetic operations")
    class ArithmeticOperations {

        @Test
        @DisplayName("should add two Money amounts")
        void shouldAddTwoMoneyAmounts() {
            Money a = Money.ofCents(1000);
            Money b = Money.ofCents(500);
            Money result = a.add(b);
            assertEquals(1500, result.toCents());
        }

        @Test
        @DisplayName("should subtract Money amounts")
        void shouldSubtractMoneyAmounts() {
            Money a = Money.ofCents(1000);
            Money b = Money.ofCents(400);
            Money result = a.subtract(b);
            assertEquals(600, result.toCents());
        }

        @Test
        @DisplayName("should throw when subtraction results in negative")
        void shouldThrowWhenSubtractionResultsInNegative() {
            Money a = Money.ofCents(100);
            Money b = Money.ofCents(200);
            assertThrows(IllegalStateException.class, () -> a.subtract(b));
        }

        @Test
        @DisplayName("should multiply by positive factor")
        void shouldMultiplyByPositiveFactor() {
            Money stake = Money.ofCents(1000);
            Money result = stake.multiply(3);
            assertEquals(3000, result.toCents());
        }

        @Test
        @DisplayName("should multiply by zero")
        void shouldMultiplyByZero() {
            Money stake = Money.ofCents(1000);
            Money result = stake.multiply(0);
            assertEquals(0, result.toCents());
        }

        @Test
        @DisplayName("should reject negative multiplier")
        void shouldRejectNegativeMultiplier() {
            Money stake = Money.ofCents(1000);
            assertThrows(IllegalArgumentException.class, () -> stake.multiply(-1));
        }
    }

    @Nested
    @DisplayName("Comparison operations")
    class ComparisonOperations {

        @Test
        @DisplayName("should compare greater than or equal")
        void shouldCompareGreaterThanOrEqual() {
            Money a = Money.ofCents(1000);
            Money b = Money.ofCents(500);
            Money c = Money.ofCents(1000);

            assertTrue(a.isGreaterThanOrEqual(b));
            assertTrue(a.isGreaterThanOrEqual(c));
            assertFalse(b.isGreaterThanOrEqual(a));
        }

        @Test
        @DisplayName("should compare less than")
        void shouldCompareLessThan() {
            Money a = Money.ofCents(500);
            Money b = Money.ofCents(1000);
            
            assertTrue(a.isLessThan(b));
            assertFalse(b.isLessThan(a));
        }

        @Test
        @DisplayName("should check if positive")
        void shouldCheckIfPositive() {
            assertTrue(Money.ofCents(1).isPositive());
            assertFalse(Money.ZERO.isPositive());
        }
    }

    @Nested
    @DisplayName("Conversion to BigDecimal")
    class ConversionToBigDecimal {

        @Test
        @DisplayName("should convert 2500 cents to EUR 25.00")
        void shouldConvertCentsToDecimal() {
            Money money = Money.ofCents(2500);
            assertEquals(new BigDecimal("25.00"), money.toDecimal());
        }

        @Test
        @DisplayName("should convert 1 cent to EUR 0.01")
        void shouldConvertOneCentToDecimal() {
            Money money = Money.ofCents(1);
            assertEquals(new BigDecimal("0.01"), money.toDecimal());
        }
    }

    @Nested
    @DisplayName("Equality and hashing")
    class EqualityAndHashing {

        @Test
        @DisplayName("should be equal for same cents")
        void shouldBeEqualForSameCents() {
            Money a = Money.ofCents(1000);
            Money b = Money.ofCents(1000);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different cents")
        void shouldNotBeEqualForDifferentCents() {
            Money a = Money.ofCents(1000);
            Money b = Money.ofCents(2000);
            assertNotEquals(a, b);
        }
    }

    @Nested
    @DisplayName("Overflow protection")
    class OverflowProtection {

        @Test
        @DisplayName("maximum stake times maximum odds should not overflow")
        void maxPayoutShouldNotOverflow() {
            // MAX_STAKE_CENTS = 1,000,000 (EUR 10,000.00)
            // Maximum odds = 4
            // Maximum payout = 4,000,000 cents (EUR 40,000.00)
            Money maxStake = Money.forStake(Money.MAX_STAKE_CENTS);

            Money payout = maxStake.multiply(4); // Max odds

            assertEquals(4_000_000L, payout.toCents(),
                "Max payout should be EUR 40,000.00 (4,000,000 cents)");
        }

        @Test
        @DisplayName("payout calculation should use Math.multiplyExact for overflow detection")
        void shouldDetectOverflowOnMultiply() {
            // Create money near Long.MAX_VALUE / 2 to trigger overflow when multiplied
            Money nearMaxMoney = Money.ofCents(Long.MAX_VALUE / 2);

            assertThrows(ArithmeticException.class,
                () -> nearMaxMoney.multiply(3),
                "Multiplication overflow should throw ArithmeticException");
        }

        @Test
        @DisplayName("addition should detect overflow")
        void shouldDetectOverflowOnAdd() {
            Money nearMax = Money.ofCents(Long.MAX_VALUE - 100);
            Money toAdd = Money.ofCents(200);

            assertThrows(ArithmeticException.class,
                () -> nearMax.add(toAdd),
                "Addition overflow should throw ArithmeticException");
        }

        @Test
        @DisplayName("realistic maximum payout scenario should succeed")
        void realisticMaxPayoutShouldSucceed() {
            // Scenario: User bets maximum stake (EUR 10,000) at odds 4
            // Expected payout: EUR 40,000
            Money stake = Money.forStake(1_000_000L); // EUR 10,000.00
            int odds = 4;

            Money payout = stake.multiply(odds);

            assertAll(
                () -> assertEquals(4_000_000L, payout.toCents()),
                () -> assertEquals(new BigDecimal("40000.00"), payout.toDecimal()),
                () -> assertTrue(payout.isPositive())
            );
        }

        @Test
        @DisplayName("multiple maximum payouts should not overflow when summed")
        void multipleMaxPayoutsShouldNotOverflow() {
            // Scenario: Settlement pays out to 1000 winning bets at max payout
            // 1000 * EUR 40,000 = EUR 40,000,000 (well within long range)
            Money singlePayout = Money.ofCents(4_000_000L);
            Money total = Money.ZERO;

            for (int i = 0; i < 1000; i++) {
                total = total.add(singlePayout);
            }

            assertEquals(4_000_000_000L, total.toCents(),
                "Total of 1000 max payouts should be EUR 40,000,000");
        }
    }
}
