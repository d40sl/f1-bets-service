package com.f1bets.security;

import com.f1bets.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for settlement safety: idempotency and double-credit prevention.
 */
class SettlementSafetyTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static int nextSessionKey = (int) (System.currentTimeMillis() % 100000) + 70000;

    private int getUniqueSessionKey() {
        return nextSessionKey++;
    }

    @Nested
    @DisplayName("Double-credit prevention")
    class DoubleCreditPrevention {

        @Test
        @DisplayName("should not double-credit when settlement replayed with same idempotency key")
        void shouldNotDoubleCreditOnIdempotentReplay() {
            String userId = "double-credit-test-" + UUID.randomUUID();
            int sessionKey = getUniqueSessionKey();
            int winningDriver = 1;

            // Place a winning bet
            String betIdempotencyKey = UUID.randomUUID().toString();
            HttpHeaders betHeaders = new HttpHeaders();
            betHeaders.setContentType(MediaType.APPLICATION_JSON);
            betHeaders.set("X-User-Id", userId);
            betHeaders.set("Idempotency-Key", betIdempotencyKey);

            Map<String, Object> betRequest = Map.of(
                "sessionKey", sessionKey,
                "driverNumber", winningDriver,
                "amount", 10.00
            );

            ResponseEntity<String> betResponse = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(betRequest, betHeaders),
                String.class
            );
            assertEquals(HttpStatus.CREATED, betResponse.getStatusCode(), "Bet should be placed successfully");

            // First settlement
            String settleIdempotencyKey = UUID.randomUUID().toString();
            HttpHeaders settleHeaders = new HttpHeaders();
            settleHeaders.setContentType(MediaType.APPLICATION_JSON);
            settleHeaders.set("Idempotency-Key", settleIdempotencyKey);

            Map<String, Object> settleRequest = Map.of("winningDriverNumber", winningDriver);

            ResponseEntity<String> firstSettleResponse = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(settleRequest, settleHeaders),
                String.class
            );
            assertEquals(HttpStatus.OK, firstSettleResponse.getStatusCode(), "First settlement should succeed");

            // Get balance after first settlement
            ResponseEntity<String> userResponse1 = restTemplate.getForEntity(
                "/api/v1/users/" + userId, String.class);
            assertEquals(HttpStatus.OK, userResponse1.getStatusCode());
            BigDecimal balanceAfterFirst = extractBalance(userResponse1.getBody());

            // Replay same settlement with same idempotency key
            ResponseEntity<String> secondSettleResponse = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(settleRequest, settleHeaders),
                String.class
            );
            // Should return same cached response (via idempotency filter)
            assertEquals(HttpStatus.OK, secondSettleResponse.getStatusCode(),
                "Idempotent replay should return 200");

            // Get balance after replay
            ResponseEntity<String> userResponse2 = restTemplate.getForEntity(
                "/api/v1/users/" + userId, String.class);
            BigDecimal balanceAfterReplay = extractBalance(userResponse2.getBody());

            assertEquals(balanceAfterFirst, balanceAfterReplay,
                "Balance should not change on idempotent replay - no double credit");
        }

        @Test
        @DisplayName("should not double-credit when same settlement requested with different idempotency key")
        void shouldNotDoubleCreditOnDifferentIdempotencyKey() {
            String userId = "double-credit-diff-key-" + UUID.randomUUID();
            int sessionKey = getUniqueSessionKey();
            int winningDriver = 44;

            // Place a winning bet
            HttpHeaders betHeaders = new HttpHeaders();
            betHeaders.setContentType(MediaType.APPLICATION_JSON);
            betHeaders.set("X-User-Id", userId);
            betHeaders.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> betRequest = Map.of(
                "sessionKey", sessionKey,
                "driverNumber", winningDriver,
                "amount", 25.00
            );

            restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(betRequest, betHeaders),
                String.class
            );

            // First settlement
            HttpHeaders settleHeaders1 = new HttpHeaders();
            settleHeaders1.setContentType(MediaType.APPLICATION_JSON);
            settleHeaders1.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> settleRequest = Map.of("winningDriverNumber", winningDriver);

            ResponseEntity<String> firstSettleResponse = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(settleRequest, settleHeaders1),
                String.class
            );
            assertEquals(HttpStatus.OK, firstSettleResponse.getStatusCode());

            // Get balance after first settlement
            ResponseEntity<String> userResponse1 = restTemplate.getForEntity(
                "/api/v1/users/" + userId, String.class);
            BigDecimal balanceAfterFirst = extractBalance(userResponse1.getBody());

            // Second settlement attempt with DIFFERENT idempotency key but same winner
            HttpHeaders settleHeaders2 = new HttpHeaders();
            settleHeaders2.setContentType(MediaType.APPLICATION_JSON);
            settleHeaders2.set("Idempotency-Key", UUID.randomUUID().toString());

            ResponseEntity<String> secondSettleResponse = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(settleRequest, settleHeaders2),
                String.class
            );
            // Should return 200 OK (idempotent for same outcome)
            assertEquals(HttpStatus.OK, secondSettleResponse.getStatusCode(),
                "Same outcome settlement should be idempotent");

            // Get balance after second attempt
            ResponseEntity<String> userResponse2 = restTemplate.getForEntity(
                "/api/v1/users/" + userId, String.class);
            BigDecimal balanceAfterSecond = extractBalance(userResponse2.getBody());

            assertEquals(balanceAfterFirst, balanceAfterSecond,
                "Balance should not change - no double credit even with different idempotency key");
        }
    }

    @Nested
    @DisplayName("Settle endpoint idempotency")
    class SettleIdempotency {

        @Test
        @DisplayName("should return 200 OK when settling with same winner again")
        void shouldReturn200WhenSettlingWithSameWinner() {
            int sessionKey = getUniqueSessionKey();
            int winningDriver = 1;

            // First settlement
            HttpHeaders headers1 = new HttpHeaders();
            headers1.setContentType(MediaType.APPLICATION_JSON);
            headers1.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of("winningDriverNumber", winningDriver);

            ResponseEntity<String> response1 = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(request, headers1),
                String.class
            );
            assertEquals(HttpStatus.OK, response1.getStatusCode());

            // Second settlement with same winner but different idempotency key
            HttpHeaders headers2 = new HttpHeaders();
            headers2.setContentType(MediaType.APPLICATION_JSON);
            headers2.set("Idempotency-Key", UUID.randomUUID().toString());

            ResponseEntity<String> response2 = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(request, headers2),
                String.class
            );

            assertEquals(HttpStatus.OK, response2.getStatusCode(),
                "Same winner settlement should return 200 OK (idempotent)");
            assertTrue(response2.getBody().contains(String.valueOf(sessionKey)));
            assertTrue(response2.getBody().contains(String.valueOf(winningDriver)));
        }

        @Test
        @DisplayName("should return 409 Conflict when settling with different winner")
        void shouldReturn409WhenSettlingWithDifferentWinner() {
            int sessionKey = getUniqueSessionKey();

            // First settlement with driver 1
            HttpHeaders headers1 = new HttpHeaders();
            headers1.setContentType(MediaType.APPLICATION_JSON);
            headers1.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request1 = Map.of("winningDriverNumber", 1);

            ResponseEntity<String> response1 = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(request1, headers1),
                String.class
            );
            assertEquals(HttpStatus.OK, response1.getStatusCode());

            // Second settlement with DIFFERENT driver 44
            HttpHeaders headers2 = new HttpHeaders();
            headers2.setContentType(MediaType.APPLICATION_JSON);
            headers2.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request2 = Map.of("winningDriverNumber", 44);

            ResponseEntity<String> response2 = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(request2, headers2),
                String.class
            );

            assertEquals(HttpStatus.CONFLICT, response2.getStatusCode(),
                "Different winner should return 409 Conflict");
            assertTrue(response2.getBody().contains("already settled") ||
                       response2.getBody().contains("Conflict"));
        }

        @Test
        @DisplayName("should return cached response when replaying with same idempotency key")
        void shouldReturnCachedResponseOnIdempotentReplay() {
            int sessionKey = getUniqueSessionKey();
            int winningDriver = 1;
            String idempotencyKey = UUID.randomUUID().toString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", idempotencyKey);

            Map<String, Object> request = Map.of("winningDriverNumber", winningDriver);

            // First request
            ResponseEntity<String> response1 = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );
            assertEquals(HttpStatus.OK, response1.getStatusCode());

            // Replay with same idempotency key
            ResponseEntity<String> response2 = restTemplate.exchange(
                "/api/v1/events/" + sessionKey + "/settle",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );

            assertEquals(HttpStatus.OK, response2.getStatusCode());
            assertEquals(response1.getBody(), response2.getBody(),
                "Idempotent replay should return identical cached response");
        }
    }

    private BigDecimal extractBalance(String jsonBody) {
        // Simple regex to extract balance from JSON response
        Pattern pattern = Pattern.compile("\"balance\"\\s*:\\s*([\\d.]+)");
        Matcher matcher = pattern.matcher(jsonBody);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }
        throw new IllegalArgumentException("Could not extract balance from: " + jsonBody);
    }
}
