package com.f1bets.integration;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.port.F1DataProvider;
import com.f1bets.application.dto.SessionQuery;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@TestConfiguration
public class TestF1DataProviderConfig {

    @Bean
    @Primary
    public F1DataProvider testF1DataProvider() {
        return new StubF1DataProvider();
    }

    /**
     * Stub F1DataProvider for integration tests.
     * Returns valid sessions with common F1 drivers for any session key.
     * Sessions are configured to have ended in the past to allow settlement testing.
     */
    static class StubF1DataProvider implements F1DataProvider {

        private static final List<EventWithDrivers.DriverInfo> TEST_DRIVERS = List.of(
            new EventWithDrivers.DriverInfo(1, "Max Verstappen", "Red Bull", 2),
            new EventWithDrivers.DriverInfo(44, "Lewis Hamilton", "Mercedes", 3),
            new EventWithDrivers.DriverInfo(16, "Charles Leclerc", "Ferrari", 3),
            new EventWithDrivers.DriverInfo(55, "Carlos Sainz", "Ferrari", 4)
        );

        @Override
        public List<EventWithDrivers> getSessions(SessionQuery query) {
            return List.of(createTestSession(9472));
        }

        @Override
        public List<EventWithDrivers> getSessions(SessionQuery query, boolean skipCache) {
            return getSessions(query);
        }

        @Override
        public Optional<EventWithDrivers> getSessionByKey(int sessionKey) {
            return Optional.of(createTestSession(sessionKey));
        }

        @Override
        public Optional<EventWithDrivers> getSessionByKey(int sessionKey, boolean skipCache) {
            return getSessionByKey(sessionKey);
        }

        private EventWithDrivers createTestSession(int sessionKey) {
            return new EventWithDrivers(
                sessionKey,
                "Test Race " + sessionKey,
                "Race",
                "Test Circuit",
                "Test Country",
                "XX",
                Instant.now().minusSeconds(7200), // Started 2 hours ago
                Instant.now().minusSeconds(3600), // Ended 1 hour ago
                2024,
                TEST_DRIVERS
            );
        }
    }
}
