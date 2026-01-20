package com.f1bets.api.dto.response;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.PlaceBetResult;
import com.f1bets.application.dto.SettleEventResult;
import com.f1bets.application.usecase.GetUserUseCase;
import com.f1bets.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResponseDtoTest {

    @Nested
    @DisplayName("BetResponse")
    class BetResponseTests {

        @Test
        @DisplayName("should create from PlaceBetResult")
        void shouldCreateFromPlaceBetResult() {
            Bet bet = Bet.place(
                UserId.of("user"),
                SessionKey.of(9158),
                DriverNumber.of(44),
                Money.ofCents(2500),
                Odds.of(3)
            );
            PlaceBetResult result = PlaceBetResult.from(bet, Money.ofCents(7500));

            BetResponse response = BetResponse.from(result);

            assertEquals(bet.getId(), response.betId());
            assertEquals(9158, response.sessionKey());
            assertEquals(44, response.driverNumber());
            assertEquals(new BigDecimal("25.00"), response.stake());
            assertEquals(3, response.odds());
            assertEquals(new BigDecimal("75.00"), response.potentialWinnings());
            assertEquals("PENDING", response.status());
            assertEquals(new BigDecimal("75.00"), response.userBalance());
        }

        @Test
        @DisplayName("should create from Bet entity with null userBalance")
        void shouldCreateFromBet() {
            Bet bet = Bet.place(
                UserId.of("user"),
                SessionKey.of(9158),
                DriverNumber.of(44),
                Money.ofCents(2500),
                Odds.of(3)
            );

            BetResponse response = BetResponse.fromBet(bet);

            assertEquals(bet.getId(), response.betId());
            assertEquals(9158, response.sessionKey());
            assertEquals(44, response.driverNumber());
            assertEquals(new BigDecimal("25.00"), response.stake());
            assertEquals(3, response.odds());
            assertEquals(new BigDecimal("75.00"), response.potentialWinnings());
            assertEquals("PENDING", response.status());
            assertNull(response.userBalance());
        }
    }

    @Nested
    @DisplayName("EventResponse")
    class EventResponseTests {

        @Test
        @DisplayName("should create from EventWithDrivers")
        void shouldCreateFromEventWithDrivers() {
            EventWithDrivers.DriverInfo driver = new EventWithDrivers.DriverInfo(
                44, "Lewis Hamilton", "Mercedes", 3
            );
            Instant start = Instant.parse("2024-07-07T14:00:00Z");
            Instant end = Instant.parse("2024-07-07T16:00:00Z");
            EventWithDrivers event = new EventWithDrivers(
                9158, "Race", "Race", "Silverstone",
                "Great Britain", "GBR", start, end, 2024,
                List.of(driver)
            );

            EventResponse response = EventResponse.from(event);

            assertEquals(9158, response.sessionKey());
            assertEquals("Race", response.sessionName());
            assertEquals("Race", response.sessionType());
            assertEquals("Silverstone", response.circuitName());
            assertEquals("Great Britain", response.countryName());
            assertEquals("GBR", response.countryCode());
            assertEquals(start, response.dateStart());
            assertEquals(end, response.dateEnd());
            assertEquals(2024, response.year());
            assertEquals(1, response.drivers().size());
            assertEquals(44, response.drivers().get(0).driverNumber());
            assertEquals("Lewis Hamilton", response.drivers().get(0).fullName());
        }
    }

    @Nested
    @DisplayName("SettleEventResponse")
    class SettleEventResponseTests {

        @Test
        @DisplayName("should create from SettleEventResult")
        void shouldCreateFromSettleEventResult() {
            SettleEventResult result = new SettleEventResult(
                9158,
                44,
                10, 3,
                Money.ofCents(15000)
            );

            SettleEventResponse response = SettleEventResponse.from(result);

            assertEquals(9158, response.sessionKey());
            assertEquals(44, response.winningDriverNumber());
            assertEquals(10, response.totalBets());
            assertEquals(3, response.winningBets());
            assertEquals(new BigDecimal("150.00"), response.totalPayout());
        }
    }

    @Nested
    @DisplayName("UserResponse")
    class UserResponseTests {

        @Test
        @DisplayName("should create from UserWithBets")
        void shouldCreateFromUserWithBets() {
            User user = User.createNew(UserId.of("john-doe"));
            Bet bet = Bet.place(
                UserId.of("john-doe"),
                SessionKey.of(9158),
                DriverNumber.of(44),
                Money.ofCents(2500),
                Odds.of(3)
            );

            GetUserUseCase.UserWithBets userWithBets = new GetUserUseCase.UserWithBets(user, List.of(bet));
            UserResponse response = UserResponse.from(userWithBets);

            assertEquals("john-doe", response.userId());
            assertEquals(new BigDecimal("100.00"), response.balance());
            assertEquals(1, response.bets().size());
        }

        @Test
        @DisplayName("should create with empty bets list")
        void shouldCreateWithEmptyBets() {
            User user = User.createNew(UserId.of("john-doe"));

            GetUserUseCase.UserWithBets userWithBets = new GetUserUseCase.UserWithBets(user, List.of());
            UserResponse response = UserResponse.from(userWithBets);

            assertEquals("john-doe", response.userId());
            assertTrue(response.bets().isEmpty());
        }
    }
}
