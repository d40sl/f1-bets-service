package com.f1bets.security;

import com.f1bets.integration.BaseIntegrationTest;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitTest extends BaseIntegrationTest {

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @Nested
    @DisplayName("Rate limiter configuration")
    class RateLimiterConfiguration {

        @Test
        @DisplayName("should have api rate limiter registered")
        void shouldHaveApiRateLimiterRegistered() {
            assertTrue(
                rateLimiterRegistry.getAllRateLimiters()
                    .stream()
                    .anyMatch(rl -> "api".equals(rl.getName())),
                "API rate limiter should be registered"
            );
        }

        @Test
        @DisplayName("should have rate limiter with correct configuration")
        void shouldHaveRateLimiterWithCorrectConfiguration() {
            var rateLimiter = rateLimiterRegistry.rateLimiter("api");
            var config = rateLimiter.getRateLimiterConfig();

            assertTrue(config.getLimitForPeriod() > 0, "Limit should be positive");
            assertNotNull(config.getLimitRefreshPeriod(), "Refresh period should be set");
        }
    }
}
