package com.f1bets.security;

import com.f1bets.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InputValidationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Nested
    @DisplayName("User ID validation")
    class UserIdValidation {

        @ParameterizedTest(name = "should reject user ID: {0}")
        @ValueSource(strings = {"", "   ", "user with spaces", "user@invalid", "user<script>"})
        @DisplayName("should reject invalid user IDs")
        void shouldRejectInvalidUserIds(String invalidUserId) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", invalidUserId);
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", 9472,
                "driverNumber", 44,
                "amount", 25.00
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
        @DisplayName("should accept valid user ID with alphanumeric and hyphens")
        void shouldAcceptValidUserId() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "valid-user-123_test-" + UUID.randomUUID());
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            int uniqueSessionKey = 100000 + (int)(Math.random() * 900000);
            Map<String, Object> request = Map.of(
                "sessionKey", uniqueSessionKey,
                "driverNumber", 44,
                "amount", 25.00
            );

            ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Bet amount validation")
    class BetAmountValidation {

        @Test
        @DisplayName("should reject negative bet amount")
        void shouldRejectNegativeAmount() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user-" + UUID.randomUUID());
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", 9472,
                "driverNumber", 44,
                "amount", -10.00
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
        @DisplayName("should reject zero bet amount")
        void shouldRejectZeroAmount() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user-" + UUID.randomUUID());
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", 9472,
                "driverNumber", 44,
                "amount", 0
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
        @DisplayName("should reject amount with more than 2 decimal places")
        void shouldRejectAmountWithTooManyDecimals() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user-" + UUID.randomUUID());
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", 9472,
                "driverNumber", 44,
                "amount", 10.001
            );

            ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Session key validation")
    class SessionKeyValidation {

        @Test
        @DisplayName("should reject negative session key")
        void shouldRejectNegativeSessionKey() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user-" + UUID.randomUUID());
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", -1,
                "driverNumber", 44,
                "amount", 25.00
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
        @DisplayName("should reject zero session key")
        void shouldRejectZeroSessionKey() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user-" + UUID.randomUUID());
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", 0,
                "driverNumber", 44,
                "amount", 25.00
            );

            ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Driver number validation")
    class DriverNumberValidation {

        @Test
        @DisplayName("should reject driver number greater than 99")
        void shouldRejectDriverNumberGreaterThan99() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user-" + UUID.randomUUID());
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", 9472,
                "driverNumber", 100,
                "amount", 25.00
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
        @DisplayName("should reject negative driver number")
        void shouldRejectNegativeDriverNumber() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user-" + UUID.randomUUID());
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            Map<String, Object> request = Map.of(
                "sessionKey", 9472,
                "driverNumber", -1,
                "amount", 25.00
            );

            ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }
}
