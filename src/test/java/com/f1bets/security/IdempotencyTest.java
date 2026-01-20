package com.f1bets.security;

import com.f1bets.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Nested
    @DisplayName("Idempotency key handling")
    class IdempotencyKeyHandling {

        @Test
        @DisplayName("should return same response for duplicate request with same idempotency key")
        void shouldReturnSameResponseForDuplicateRequest() {
            String userId = "idempotency-user-" + UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", userId);
            headers.set("Idempotency-Key", idempotencyKey);

            Map<String, Object> request = Map.of(
                "sessionKey", 9472,
                "driverNumber", 44,
                "amount", 25.00
            );

            ResponseEntity<String> firstResponse = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );

            ResponseEntity<String> secondResponse = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );

            assertEquals(HttpStatus.CREATED, firstResponse.getStatusCode());
            assertEquals(HttpStatus.CREATED, secondResponse.getStatusCode());
            assertEquals(firstResponse.getBody(), secondResponse.getBody());
        }

        @Test
        @DisplayName("should return 409 when same idempotency key used with different request body")
        void shouldReturn409ForConflictingRequest() {
            String userId = "conflict-user-" + UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", userId);
            headers.set("Idempotency-Key", idempotencyKey);

            Map<String, Object> firstRequest = Map.of(
                "sessionKey", 9472,
                "driverNumber", 44,
                "amount", 25.00
            );

            restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(firstRequest, headers),
                String.class
            );

            Map<String, Object> differentRequest = Map.of(
                "sessionKey", 9472,
                "driverNumber", 1,
                "amount", 50.00
            );

            ResponseEntity<String> conflictResponse = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(differentRequest, headers),
                String.class
            );

            assertEquals(HttpStatus.CONFLICT, conflictResponse.getStatusCode());
            assertTrue(conflictResponse.getBody().contains("conflict"));
        }

        @Test
        @DisplayName("should allow different idempotency keys for different requests")
        void shouldAllowDifferentIdempotencyKeys() {
            String userId = "multi-request-user-" + UUID.randomUUID();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", userId);

            Map<String, Object> firstRequest = Map.of(
                "sessionKey", 9472,
                "driverNumber", 44,
                "amount", 10.00
            );

            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            ResponseEntity<String> firstResponse = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(firstRequest, headers),
                String.class
            );

            Map<String, Object> secondRequest = Map.of(
                "sessionKey", 9473,
                "driverNumber", 1,
                "amount", 10.00
            );

            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            ResponseEntity<String> secondResponse = restTemplate.exchange(
                "/api/v1/bets",
                HttpMethod.POST,
                new HttpEntity<>(secondRequest, headers),
                String.class
            );

            assertEquals(HttpStatus.CREATED, firstResponse.getStatusCode());
            assertEquals(HttpStatus.CREATED, secondResponse.getStatusCode());
        }

        @Test
        @DisplayName("should return 400 when idempotency key header is missing")
        void shouldReturn400WhenIdempotencyKeyMissing() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user-" + UUID.randomUUID());

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
            assertTrue(response.getBody().contains("Idempotency-Key"));
        }

        @Test
        @DisplayName("should return 400 when idempotency key header is blank")
        void shouldReturn400WhenIdempotencyKeyBlank() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user-" + UUID.randomUUID());
            headers.set("Idempotency-Key", "   ");

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
        @DisplayName("should return 400 when idempotency key is not a valid UUID")
        void shouldReturn400WhenIdempotencyKeyInvalidFormat() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "test-user-" + UUID.randomUUID());
            headers.set("Idempotency-Key", "not-a-valid-uuid");

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
            assertTrue(response.getBody().contains("UUID"));
        }

        @Test
        @DisplayName("should handle concurrent requests with same idempotency key - only one succeeds")
        void shouldHandleConcurrentRequestsWithSameIdempotencyKey() throws InterruptedException, ExecutionException {
            String userId = "concurrent-idem-user-" + UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            int numberOfThreads = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

            List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
            for (int i = 0; i < numberOfThreads; i++) {
                tasks.add(() -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("X-User-Id", userId);
                    headers.set("Idempotency-Key", idempotencyKey);

                    Map<String, Object> request = Map.of(
                        "sessionKey", 9999,
                        "driverNumber", 44,
                        "amount", 10.00
                    );

                    return restTemplate.exchange(
                        "/api/v1/bets",
                        HttpMethod.POST,
                        new HttpEntity<>(request, headers),
                        String.class
                    );
                });
            }

            List<Future<ResponseEntity<String>>> futures = executor.invokeAll(tasks);
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            AtomicInteger createdCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            String firstResponseBody = null;

            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get();
                if (response.getStatusCode() == HttpStatus.CREATED) {
                    createdCount.incrementAndGet();
                    if (firstResponseBody == null) {
                        firstResponseBody = response.getBody();
                    } else {
                        assertEquals(firstResponseBody, response.getBody(), 
                            "All CREATED responses should be identical (cached)");
                    }
                } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            }

            assertTrue(createdCount.get() >= 1, "At least one request should succeed");
            assertEquals(numberOfThreads, createdCount.get() + conflictCount.get(), 
                "All requests should either succeed or return conflict");
        }
    }
}
