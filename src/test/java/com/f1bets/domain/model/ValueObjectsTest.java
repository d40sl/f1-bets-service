package com.f1bets.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValueObjectsTest {

    @Nested
    @DisplayName("DriverNumber")
    class DriverNumberTests {

        @Test
        @DisplayName("should create valid driver number")
        void shouldCreateValidDriverNumber() {
            DriverNumber dn = DriverNumber.of(44);
            assertEquals(44, dn.getValue());
        }

        @Test
        @DisplayName("should throw for zero driver number")
        void shouldThrowForZero() {
            assertThrows(IllegalArgumentException.class, () -> DriverNumber.of(0));
        }

        @Test
        @DisplayName("should throw for negative driver number")
        void shouldThrowForNegative() {
            assertThrows(IllegalArgumentException.class, () -> DriverNumber.of(-1));
        }

        @Test
        @DisplayName("should throw for driver number exceeding 99")
        void shouldThrowForExceeding99() {
            assertThrows(IllegalArgumentException.class, () -> DriverNumber.of(100));
        }

        @Test
        @DisplayName("should accept boundary values 1 and 99")
        void shouldAcceptBoundaryValues() {
            assertEquals(1, DriverNumber.of(1).getValue());
            assertEquals(99, DriverNumber.of(99).getValue());
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            DriverNumber dn1 = DriverNumber.of(44);
            DriverNumber dn2 = DriverNumber.of(44);
            DriverNumber dn3 = DriverNumber.of(1);

            assertEquals(dn1, dn2);
            assertEquals(dn1.hashCode(), dn2.hashCode());
            assertNotEquals(dn1, dn3);
        }
    }

    @Nested
    @DisplayName("SessionKey")
    class SessionKeyTests {

        @Test
        @DisplayName("should create valid session key")
        void shouldCreateValidSessionKey() {
            SessionKey sk = SessionKey.of(9158);
            assertEquals(9158, sk.getValue());
        }

        @Test
        @DisplayName("should throw for zero session key")
        void shouldThrowForZero() {
            assertThrows(IllegalArgumentException.class, () -> SessionKey.of(0));
        }

        @Test
        @DisplayName("should throw for negative session key")
        void shouldThrowForNegative() {
            assertThrows(IllegalArgumentException.class, () -> SessionKey.of(-1));
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            SessionKey sk1 = SessionKey.of(9158);
            SessionKey sk2 = SessionKey.of(9158);
            SessionKey sk3 = SessionKey.of(9159);

            assertEquals(sk1, sk2);
            assertEquals(sk1.hashCode(), sk2.hashCode());
            assertNotEquals(sk1, sk3);
        }
    }

    @Nested
    @DisplayName("UserId")
    class UserIdTests {

        @Test
        @DisplayName("should create valid user ID")
        void shouldCreateValidUserId() {
            UserId uid = UserId.of("john-doe-123");
            assertEquals("john-doe-123", uid.getValue());
        }

        @Test
        @DisplayName("should throw for null user ID")
        void shouldThrowForNull() {
            assertThrows(IllegalArgumentException.class, () -> UserId.of(null));
        }

        @Test
        @DisplayName("should throw for empty user ID")
        void shouldThrowForEmpty() {
            assertThrows(IllegalArgumentException.class, () -> UserId.of(""));
        }

        @Test
        @DisplayName("should throw for blank user ID")
        void shouldThrowForBlank() {
            assertThrows(IllegalArgumentException.class, () -> UserId.of("   "));
        }

        @Test
        @DisplayName("should accept alphanumeric with hyphens and underscores")
        void shouldAcceptValidCharacters() {
            assertEquals("user_123", UserId.of("user_123").getValue());
            assertEquals("user-456", UserId.of("user-456").getValue());
            assertEquals("USER789", UserId.of("USER789").getValue());
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            UserId uid1 = UserId.of("john-doe");
            UserId uid2 = UserId.of("john-doe");
            UserId uid3 = UserId.of("jane-doe");

            assertEquals(uid1, uid2);
            assertEquals(uid1.hashCode(), uid2.hashCode());
            assertNotEquals(uid1, uid3);
        }
    }
}
