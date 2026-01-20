package com.f1bets.api.exception;

import com.f1bets.api.dto.response.ErrorResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("Rate limit exception handling")
    class RateLimitExceptionHandling {

        @Test
        @DisplayName("should return 429 with proper error message when rate limit exceeded")
        void shouldReturn429WhenRateLimitExceeded() {
            when(request.getRequestURI()).thenReturn("/api/v1/bets");

            RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
            RateLimiter rateLimiter = RateLimiter.of("test", config);
            RequestNotPermitted exception = RequestNotPermitted.createRequestNotPermitted(rateLimiter);

            ResponseEntity<ErrorResponse> response = handler.handleRateLimitExceeded(exception, request);

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(429, response.getBody().status());
            assertEquals("Too Many Requests", response.getBody().error());
            assertTrue(response.getBody().message().contains("Rate limit exceeded"));
            assertEquals("/api/v1/bets", response.getBody().path());
        }

        @Test
        @DisplayName("should include request path in error response")
        void shouldIncludeRequestPathInErrorResponse() {
            when(request.getRequestURI()).thenReturn("/api/v1/events");

            RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
            RateLimiter rateLimiter = RateLimiter.of("api", config);
            RequestNotPermitted exception = RequestNotPermitted.createRequestNotPermitted(rateLimiter);

            ResponseEntity<ErrorResponse> response = handler.handleRateLimitExceeded(exception, request);

            assertEquals("/api/v1/events", response.getBody().path());
        }
    }
}
