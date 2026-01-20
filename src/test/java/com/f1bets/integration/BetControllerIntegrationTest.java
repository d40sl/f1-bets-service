package com.f1bets.integration;

import com.f1bets.api.dto.response.BetResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BetControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Nested
    @DisplayName("POST /api/v1/bets - Place bet")
    class PlaceBet {

        @Test
        @DisplayName("should create bet and return 201 with bet details")
        void shouldCreateBetSuccessfully() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "integration-test-user-" + UUID.randomUUID());
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", 9472,
                "driverNumber", 44,
                "amount", 50.00
            );

            ResponseEntity<BetResponse> response = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                BetResponse.class
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertNotNull(response.getBody().betId());
            assertEquals(9472, response.getBody().sessionKey());
            assertEquals(44, response.getBody().driverNumber());
            assertEquals("PENDING", response.getBody().status());
            assertTrue(response.getBody().odds() >= 2 && response.getBody().odds() <= 4);
        }

        @Test
        @DisplayName("should return 400 when session key is missing")
        void shouldReturn400WhenSessionKeyMissing() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user");
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "driverNumber", 44,
                "amount", 50.00
            );

            ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("should return 400 when amount exceeds maximum")
        void shouldReturn400WhenAmountExceedsMax() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user");
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", 9472,
                "driverNumber", 44,
                "amount", 15000.00
            );

            ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("should deduct balance from user account")
        void shouldDeductUserBalance() {
            String userId = "balance-test-user-" + UUID.randomUUID();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", userId);
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", 9472,
                "driverNumber", 1,
                "amount", 30.00
            );

            ResponseEntity<BetResponse> response = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                BetResponse.class
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals("70.00", response.getBody().userBalance().toPlainString());
        }
    }

    @Nested
    @DisplayName("Balance and insufficient funds")
    class BalanceTests {

        @Test
        @DisplayName("should return 402 when user has insufficient balance")
        void shouldReturn402WhenInsufficientBalance() {
            String userId = "poor-user-" + UUID.randomUUID();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", userId);

            Map<String, Object> firstBet = Map.of(
                "sessionKey", 9472,
                "driverNumber", 44,
                "amount", 90.00
            );
            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            restTemplate.exchange("/api/v1/bets", HttpMethod.POST, new HttpEntity<>(firstBet, headers), String.class);

            Map<String, Object> secondBet = Map.of(
                "sessionKey", 9473,
                "driverNumber", 1,
                "amount", 50.00
            );
            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(secondBet, headers),
                String.class
            );

            assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
            assertTrue(response.getBody().contains("Insufficient"));
        }
    }

    @Nested
    @DisplayName("Betting on settled events")
    class SettledEventTests {

        @Test
        @DisplayName("should return 409 when trying to bet on already settled event")
        void shouldReturn409WhenBettingOnSettledEvent() {
            int sessionKey = 88888;
            String userId = "settled-event-user-" + UUID.randomUUID();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", userId);
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> betRequest = Map.of(
                "sessionKey", sessionKey,
                "driverNumber", 44,
                "amount", 10.00
            );
            restTemplate.exchange("/api/v1/bets", HttpMethod.POST,
                new HttpEntity<>(betRequest, headers), String.class);

            HttpHeaders settleHeaders = new HttpHeaders();
            settleHeaders.setContentType(MediaType.APPLICATION_JSON);
            settleHeaders.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> settleRequest = Map.of("winningDriverNumber", 1);
            restTemplate.exchange("/api/v1/events/" + sessionKey + "/settle", HttpMethod.POST,
                new HttpEntity<>(settleRequest, settleHeaders), String.class);

            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            Map<String, Object> lateBet = Map.of(
                "sessionKey", sessionKey,
                "driverNumber", 1,
                "amount", 10.00
            );
            ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(lateBet, headers),
                String.class
            );

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertTrue(response.getBody().contains("settled") || response.getBody().contains("Conflict"));
        }
    }

    @Nested
    @DisplayName("Event settlement HTTP layer")
    class EventSettlementTests {

        // Use unique session keys per test to avoid conflicts between test runs
        private static int nextSessionKey = (int) (System.currentTimeMillis() % 10000) + 70000;

        @Test
        @DisplayName("should return 409 Conflict when attempting to settle already settled event")
        void shouldReturn409WhenSettlingAlreadySettledEvent() {
            int sessionKey = nextSessionKey++;
            String userId = "double-settle-user-" + UUID.randomUUID();

            // First, place a bet so there's something to settle
            HttpHeaders betHeaders = new HttpHeaders();
            betHeaders.setContentType(MediaType.APPLICATION_JSON);
            betHeaders.set("X-User-Id", userId);
            betHeaders.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> betRequest = Map.of(
                "sessionKey", sessionKey,
                "driverNumber", 1,
                "amount", 10.00
            );
            restTemplate.exchange("/api/v1/bets", HttpMethod.POST,
                new HttpEntity<>(betRequest, betHeaders), String.class);

            // First settlement - should succeed
            HttpHeaders settleHeaders = new HttpHeaders();
            settleHeaders.setContentType(MediaType.APPLICATION_JSON);
            settleHeaders.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> settleRequest = Map.of("winningDriverNumber", 1);
            ResponseEntity<String> firstSettlement = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(settleRequest, settleHeaders),
                String.class
            );

            assertEquals(HttpStatus.OK, firstSettlement.getStatusCode(),
                "First settlement should succeed");

            // Second settlement with DIFFERENT winner - should return 409 Conflict
            settleHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
            Map<String, Object> differentWinnerRequest = Map.of("winningDriverNumber", 44);
            ResponseEntity<String> secondSettlement = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(differentWinnerRequest, settleHeaders),
                String.class
            );

            assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, secondSettlement.getStatusCode(),
                    "Settlement with different winner should return 409 Conflict"),
                () -> assertTrue(secondSettlement.getBody().contains("already settled") ||
                                 secondSettlement.getBody().contains("Conflict"),
                    "Response should indicate event is already settled")
            );
        }

        @Test
        @DisplayName("should return 409 when settling with same idempotency key but different winning driver")
        void shouldReturn409WhenIdempotencyKeyReusedWithDifferentWinner() {
            int sessionKey = nextSessionKey++;
            String userId = "idem-settle-user-" + UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();

            // Place a bet first
            HttpHeaders betHeaders = new HttpHeaders();
            betHeaders.setContentType(MediaType.APPLICATION_JSON);
            betHeaders.set("X-User-Id", userId);
            betHeaders.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> betRequest = Map.of(
                "sessionKey", sessionKey,
                "driverNumber", 1,
                "amount", 10.00
            );
            restTemplate.exchange("/api/v1/bets", HttpMethod.POST,
                new HttpEntity<>(betRequest, betHeaders), String.class);

            // First settlement with driver 1
            HttpHeaders settleHeaders = new HttpHeaders();
            settleHeaders.setContentType(MediaType.APPLICATION_JSON);
            settleHeaders.set("Idempotency-Key", idempotencyKey);

            Map<String, Object> firstSettle = Map.of("winningDriverNumber", 1);
            restTemplate.exchange("/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST, new HttpEntity<>(firstSettle, settleHeaders), String.class);

            // Attempt to settle with SAME idempotency key but DIFFERENT winning driver
            Map<String, Object> differentSettle = Map.of("winningDriverNumber", 44);
            ResponseEntity<String> conflictResponse = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(differentSettle, settleHeaders),
                String.class
            );

            assertEquals(HttpStatus.CONFLICT, conflictResponse.getStatusCode(),
                "Reusing idempotency key with different request should return 409");
        }

        @Test
        @DisplayName("should return same response when settlement replayed with same idempotency key")
        void shouldReturnCachedResponseForReplayedSettlement() {
            int sessionKey = nextSessionKey++;
            String userId = "replay-settle-user-" + UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();

            // Place a bet first
            HttpHeaders betHeaders = new HttpHeaders();
            betHeaders.setContentType(MediaType.APPLICATION_JSON);
            betHeaders.set("X-User-Id", userId);
            betHeaders.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> betRequest = Map.of(
                "sessionKey", sessionKey,
                "driverNumber", 1,
                "amount", 10.00
            );
            restTemplate.exchange("/api/v1/bets", HttpMethod.POST,
                new HttpEntity<>(betRequest, betHeaders), String.class);

            // First settlement
            HttpHeaders settleHeaders = new HttpHeaders();
            settleHeaders.setContentType(MediaType.APPLICATION_JSON);
            settleHeaders.set("Idempotency-Key", idempotencyKey);

            Map<String, Object> settleRequest = Map.of("winningDriverNumber", 1);
            ResponseEntity<String> firstResponse = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(settleRequest, settleHeaders),
                String.class
            );

            // Replay with same idempotency key and same request
            ResponseEntity<String> replayResponse = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(settleRequest, settleHeaders),
                String.class
            );

            assertAll(
                () -> assertEquals(firstResponse.getStatusCode(), replayResponse.getStatusCode(),
                    "Replayed request should return same status code"),
                () -> assertEquals(firstResponse.getBody(), replayResponse.getBody(),
                    "Replayed request should return identical response body")
            );
        }
    }
}
