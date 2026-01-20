package com.f1bets.domain.exception;

import com.f1bets.domain.model.DriverNumber;
import com.f1bets.domain.model.Money;
import com.f1bets.domain.model.SessionKey;
import com.f1bets.domain.model.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainExceptionsTest {

    @Nested
    @DisplayName("InsufficientBalanceException")
    class InsufficientBalanceExceptionTests {

        @Test
        @DisplayName("should contain current and required balance in message")
        void shouldContainBalancesInMessage() {
            Money current = Money.ofCents(5000);
            Money required = Money.ofCents(10000);

            InsufficientBalanceException ex = new InsufficientBalanceException(current, required);

            assertTrue(ex.getMessage().contains("EUR 50.00"));
            assertTrue(ex.getMessage().contains("EUR 100.00"));
            assertTrue(ex.getMessage().contains("Insufficient balance"));
        }
    }

    @Nested
    @DisplayName("EventAlreadySettledException")
    class EventAlreadySettledExceptionTests {

        @Test
        @DisplayName("should contain session key in message")
        void shouldContainSessionKeyInMessage() {
            SessionKey sessionKey = SessionKey.of(9158);

            EventAlreadySettledException ex = new EventAlreadySettledException(sessionKey);

            assertTrue(ex.getMessage().contains("9158"));
            assertTrue(ex.getMessage().contains("already settled"));
        }
    }

    @Nested
    @DisplayName("UserNotFoundException")
    class UserNotFoundExceptionTests {

        @Test
        @DisplayName("should contain user ID in message")
        void shouldContainUserIdInMessage() {
            UserNotFoundException ex = new UserNotFoundException(UserId.of("john-doe-123"));

            assertTrue(ex.getMessage().contains("john-doe-123"));
            assertTrue(ex.getMessage().toLowerCase().contains("not found"));
        }
    }

    @Nested
    @DisplayName("BetNotFoundException")
    class BetNotFoundExceptionTests {

        @Test
        @DisplayName("should contain bet ID in message")
        void shouldContainBetIdInMessage() {
            UUID betId = UUID.randomUUID();

            BetNotFoundException ex = new BetNotFoundException(betId);

            assertTrue(ex.getMessage().contains(betId.toString()));
            assertTrue(ex.getMessage().toLowerCase().contains("not found"));
        }
    }

    @Nested
    @DisplayName("SessionNotFoundException")
    class SessionNotFoundExceptionTests {

        @Test
        @DisplayName("should contain session key in message")
        void shouldContainSessionKeyInMessage() {
            SessionKey sessionKey = SessionKey.of(9472);

            SessionNotFoundException ex = new SessionNotFoundException(sessionKey);

            assertTrue(ex.getMessage().contains("9472"));
            assertTrue(ex.getMessage().toLowerCase().contains("not found"));
            assertEquals(sessionKey, ex.getSessionKey());
        }
    }

    @Nested
    @DisplayName("DriverNotInSessionException")
    class DriverNotInSessionExceptionTests {

        @Test
        @DisplayName("should contain session key and driver number in message")
        void shouldContainSessionKeyAndDriverInMessage() {
            SessionKey sessionKey = SessionKey.of(9472);
            DriverNumber driverNumber = DriverNumber.of(44);

            DriverNotInSessionException ex = new DriverNotInSessionException(sessionKey, driverNumber);

            assertTrue(ex.getMessage().contains("9472"));
            assertTrue(ex.getMessage().contains("44"));
            assertTrue(ex.getMessage().toLowerCase().contains("did not participate"));
            assertEquals(sessionKey, ex.getSessionKey());
            assertEquals(driverNumber, ex.getDriverNumber());
        }
    }

    @Nested
    @DisplayName("EventNotEndedException")
    class EventNotEndedExceptionTests {

        @Test
        @DisplayName("should contain session key and end time in message")
        void shouldContainSessionKeyAndEndTimeInMessage() {
            SessionKey sessionKey = SessionKey.of(9472);
            Instant dateEnd = Instant.parse("2024-06-01T18:00:00Z");
            Instant currentTime = Instant.parse("2024-06-01T16:00:00Z");

            EventNotEndedException ex = new EventNotEndedException(sessionKey, dateEnd, currentTime);

            assertTrue(ex.getMessage().contains("9472"));
            assertTrue(ex.getMessage().contains("2024-06-01T18:00:00Z"));
            assertTrue(ex.getMessage().toLowerCase().contains("has not ended"));
            assertEquals(sessionKey, ex.getSessionKey());
            assertEquals(dateEnd, ex.getDateEnd());
            assertEquals(currentTime, ex.getCurrentTime());
        }

        @Test
        @DisplayName("should handle null end date")
        void shouldHandleNullEndDate() {
            SessionKey sessionKey = SessionKey.of(9472);
            Instant currentTime = Instant.parse("2024-06-01T16:00:00Z");

            EventNotEndedException ex = new EventNotEndedException(sessionKey, null, currentTime);

            assertTrue(ex.getMessage().contains("9472"));
            assertTrue(ex.getMessage().contains("not available"));
            assertNull(ex.getDateEnd());
        }
    }

    @Nested
    @DisplayName("ExternalServiceUnavailableException")
    class ExternalServiceUnavailableExceptionTests {

        @Test
        @DisplayName("should contain service name in message")
        void shouldContainServiceNameInMessage() {
            ExternalServiceUnavailableException ex = new ExternalServiceUnavailableException(
                "OpenF1", "Service temporarily unavailable", new RuntimeException("Connection refused")
            );

            assertEquals("OpenF1", ex.getServiceName());
            assertTrue(ex.getMessage().contains("temporarily unavailable"));
            assertNotNull(ex.getCause());
        }
    }

}
