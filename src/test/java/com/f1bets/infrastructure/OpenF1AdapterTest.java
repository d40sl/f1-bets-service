package com.f1bets.infrastructure;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.SessionQuery;
import com.f1bets.application.service.OddsCalculator;
import com.f1bets.domain.exception.ExternalServiceUnavailableException;
import com.f1bets.domain.model.Odds;
import com.f1bets.infrastructure.external.openf1.OpenF1Adapter;
import com.f1bets.infrastructure.external.openf1.OpenF1Client;
import com.f1bets.infrastructure.external.openf1.dto.OpenF1Driver;
import com.f1bets.infrastructure.external.openf1.dto.OpenF1Session;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenF1AdapterTest {

    @Mock
    private OpenF1Client openF1Client;

    @Mock
    private OddsCalculator oddsCalculator;

    private Cache<SessionQuery, List<EventWithDrivers>> cache;
    private Cache<Integer, Optional<EventWithDrivers>> sessionKeyCache;
    private OpenF1Adapter adapter;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(100)
            .build();
        sessionKeyCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(100)
            .build();
        adapter = new OpenF1Adapter(openF1Client, cache, sessionKeyCache, oddsCalculator, 180, 6, 500);

        lenient().when(oddsCalculator.calculate(anyInt(), anyInt())).thenReturn(Odds.of(3));
    }

    @Nested
    @DisplayName("Caching behavior")
    class CachingBehavior {

        @Test
        @DisplayName("should cache session data and return cached result on subsequent calls")
        void shouldCacheSessionData() {
            SessionQuery query = SessionQuery.of("Race", 2024, "IT");
            OpenF1Session session = new OpenF1Session(
                9472, "Italian Grand Prix", "Race", "Monza", "Italy", "IT",
                "2024-09-01T13:00:00Z", "2024-09-01T15:00:00Z", 2024
            );
            OpenF1Driver driver = new OpenF1Driver(16, "Charles Leclerc", "Ferrari", "LEC");

            when(openF1Client.getSessions("Race", 2024, "IT")).thenReturn(List.of(session));
            when(openF1Client.getDrivers(9472)).thenReturn(List.of(driver));

            List<EventWithDrivers> firstCall = adapter.getSessions(query);
            List<EventWithDrivers> secondCall = adapter.getSessions(query);

            assertEquals(1, firstCall.size());
            assertEquals(1, secondCall.size());
            assertFalse(firstCall.get(0).drivers().isEmpty());
            verify(openF1Client, times(1)).getSessions(any(), any(), any());
        }

        @Test
        @DisplayName("should bypass cache for different query parameters")
        void shouldBypassCacheForDifferentQueries() {
            SessionQuery raceQuery = SessionQuery.of("Race", 2024, null);
            SessionQuery qualifyingQuery = SessionQuery.of("Qualifying", 2024, null);

            OpenF1Session raceSession = new OpenF1Session(
                9472, "GP", "Race", "Monza", "Italy", "IT",
                "2024-09-01T13:00:00Z", "2024-09-01T15:00:00Z", 2024
            );
            OpenF1Session qualifyingSession = new OpenF1Session(
                9471, "GP Qualifying", "Qualifying", "Monza", "Italy", "IT",
                "2024-08-31T14:00:00Z", "2024-08-31T15:00:00Z", 2024
            );
            OpenF1Driver driver = new OpenF1Driver(1, "Max Verstappen", "Red Bull Racing", "VER");

            when(openF1Client.getSessions("Race", 2024, null)).thenReturn(List.of(raceSession));
            when(openF1Client.getSessions("Qualifying", 2024, null)).thenReturn(List.of(qualifyingSession));
            when(openF1Client.getDrivers(anyInt())).thenReturn(List.of(driver));

            adapter.getSessions(raceQuery);
            adapter.getSessions(qualifyingQuery);

            verify(openF1Client).getSessions("Race", 2024, null);
            verify(openF1Client).getSessions("Qualifying", 2024, null);
        }
    }

    @Nested
    @DisplayName("Fallback behavior")
    class FallbackBehavior {

        @Test
        @DisplayName("should return cached data on fallback when cache exists")
        void shouldReturnCachedDataOnFallback() {
            SessionQuery query = SessionQuery.of("Race", 2024, null);
            List<EventWithDrivers> cachedEvents = List.of(
                new EventWithDrivers(9472, "GP", "Race", "Monza", "Italy", "IT", null, null, 2024, List.of())
            );
            cache.put(query, cachedEvents);

            List<EventWithDrivers> result = adapter.getSessionsFallback(query, new RuntimeException("API Error"));

            assertEquals(1, result.size());
            assertEquals(9472, result.get(0).sessionKey());
        }

        @Test
        @DisplayName("should throw ExternalServiceUnavailableException on fallback when no cache exists")
        void shouldThrowExceptionOnFallbackWithNoCache() {
            SessionQuery query = SessionQuery.of("Race", 2024, null);

            ExternalServiceUnavailableException ex = assertThrows(
                ExternalServiceUnavailableException.class,
                () -> adapter.getSessionsFallback(query, new RuntimeException("API Error"))
            );

            assertEquals("OpenF1", ex.getServiceName());
            assertTrue(ex.getMessage().contains("temporarily unavailable"));
        }
    }

    @Nested
    @DisplayName("Driver data mapping")
    class DriverDataMapping {

        @Test
        @DisplayName("should map drivers with computed odds")
        void shouldMapDriversWithComputedOdds() {
            SessionQuery query = SessionQuery.of("Race", 2024, null);
            OpenF1Session session = new OpenF1Session(
                9472, "GP", "Race", "Monza", "Italy", "IT",
                "2024-09-01T13:00:00Z", "2024-09-01T15:00:00Z", 2024
            );
            OpenF1Driver driver = new OpenF1Driver(44, "Lewis Hamilton", "Mercedes", "HAM");

            when(openF1Client.getSessions("Race", 2024, null)).thenReturn(List.of(session));
            when(openF1Client.getDrivers(9472)).thenReturn(List.of(driver));

            List<EventWithDrivers> events = adapter.getSessions(query);

            assertEquals(1, events.size());
            assertEquals(1, events.get(0).drivers().size());

            var driverInfo = events.get(0).drivers().get(0);
            assertEquals(44, driverInfo.driverNumber());
            assertEquals("Lewis Hamilton", driverInfo.fullName());
            assertEquals("Mercedes", driverInfo.teamName());
            assertTrue(driverInfo.odds() >= 2 && driverInfo.odds() <= 4);
        }

        @Test
        @DisplayName("should throw ExternalServiceUnavailableException when driver fetch fails")
        void shouldThrowExceptionWhenDriverFetchFails() {
            // Driver fetch failures are propagated as 503 errors to signal service unavailability
            // rather than silently returning empty driver lists
            SessionQuery query = SessionQuery.of("Race", 2024, null);
            OpenF1Session session = new OpenF1Session(
                9472, "GP", "Race", "Monza", "Italy", "IT",
                "2024-09-01T13:00:00Z", "2024-09-01T15:00:00Z", 2024
            );

            when(openF1Client.getSessions("Race", 2024, null)).thenReturn(List.of(session));
            when(openF1Client.getDrivers(9472)).thenThrow(new RuntimeException("Driver API error"));

            ExternalServiceUnavailableException ex = assertThrows(
                ExternalServiceUnavailableException.class,
                () -> adapter.getSessions(query)
            );

            assertEquals("OpenF1", ex.getServiceName());
            assertTrue(ex.getMessage().contains("Failed to fetch driver data"));
        }

        @Test
        @DisplayName("should not cache when driver fetch fails")
        void shouldNotCacheWhenDriverFetchFails() {
            SessionQuery query = SessionQuery.of("Race", 2024, "FR");
            OpenF1Session session = new OpenF1Session(
                9999, "French GP", "Race", "Paul Ricard", "France", "FR",
                "2024-07-01T13:00:00Z", "2024-07-01T15:00:00Z", 2024
            );

            when(openF1Client.getSessions("Race", 2024, "FR")).thenReturn(List.of(session));
            when(openF1Client.getDrivers(9999)).thenThrow(new RuntimeException("Driver API error"));

            // Driver fetch failure throws exception
            assertThrows(ExternalServiceUnavailableException.class,
                () -> adapter.getSessions(query));

            // Nothing should be cached
            assertNull(cache.getIfPresent(query),
                "Failed requests should not be cached");
        }

        @Test
        @DisplayName("should cache results when all events have drivers")
        void shouldCacheResultsWithDrivers() {
            SessionQuery query = SessionQuery.of("Race", 2024, "ES");
            OpenF1Session session = new OpenF1Session(
                8888, "Spanish GP", "Race", "Barcelona", "Spain", "ES",
                "2024-06-01T13:00:00Z", "2024-06-01T15:00:00Z", 2024
            );
            OpenF1Driver driver = new OpenF1Driver(1, "Max Verstappen", "Red Bull Racing", "VER");

            when(openF1Client.getSessions("Race", 2024, "ES")).thenReturn(List.of(session));
            when(openF1Client.getDrivers(8888)).thenReturn(List.of(driver));

            adapter.getSessions(query);

            assertNotNull(cache.getIfPresent(query), "Complete results should be cached");
        }
    }

    @Nested
    @DisplayName("getSessionByKey behavior")
    class GetSessionByKeyBehavior {

        @Test
        @DisplayName("should return session when found by key")
        void shouldReturnSessionWhenFound() {
            int sessionKey = 9472;
            OpenF1Session session = new OpenF1Session(
                sessionKey, "Italian Grand Prix", "Race", "Monza", "Italy", "IT",
                "2024-09-01T13:00:00Z", "2024-09-01T15:00:00Z", 2024
            );
            OpenF1Driver driver = new OpenF1Driver(44, "Lewis Hamilton", "Mercedes", "HAM");

            when(openF1Client.getSessionByKey(sessionKey)).thenReturn(List.of(session));
            when(openF1Client.getDrivers(sessionKey)).thenReturn(List.of(driver));

            Optional<EventWithDrivers> result = adapter.getSessionByKey(sessionKey);

            assertTrue(result.isPresent());
            assertEquals(sessionKey, result.get().sessionKey());
            assertEquals("Italian Grand Prix", result.get().sessionName());
            assertEquals(1, result.get().drivers().size());
            assertEquals(44, result.get().drivers().get(0).driverNumber());
        }

        @Test
        @DisplayName("should return empty when session not found")
        void shouldReturnEmptyWhenNotFound() {
            int sessionKey = 99999;
            when(openF1Client.getSessionByKey(sessionKey)).thenReturn(List.of());

            Optional<EventWithDrivers> result = adapter.getSessionByKey(sessionKey);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should cache session by key")
        void shouldCacheSessionByKey() {
            int sessionKey = 9472;
            OpenF1Session session = new OpenF1Session(
                sessionKey, "GP", "Race", "Monza", "Italy", "IT",
                "2024-09-01T13:00:00Z", "2024-09-01T15:00:00Z", 2024
            );
            OpenF1Driver driver = new OpenF1Driver(44, "Lewis Hamilton", "Mercedes", "HAM");

            when(openF1Client.getSessionByKey(sessionKey)).thenReturn(List.of(session));
            when(openF1Client.getDrivers(sessionKey)).thenReturn(List.of(driver));

            adapter.getSessionByKey(sessionKey);
            adapter.getSessionByKey(sessionKey);

            verify(openF1Client, times(1)).getSessionByKey(sessionKey);
        }

        @Test
        @DisplayName("should skip cache when requested")
        void shouldSkipCacheWhenRequested() {
            int sessionKey = 9472;
            OpenF1Session session = new OpenF1Session(
                sessionKey, "GP", "Race", "Monza", "Italy", "IT",
                "2024-09-01T13:00:00Z", "2024-09-01T15:00:00Z", 2024
            );
            OpenF1Driver driver = new OpenF1Driver(44, "Lewis Hamilton", "Mercedes", "HAM");

            when(openF1Client.getSessionByKey(sessionKey)).thenReturn(List.of(session));
            when(openF1Client.getDrivers(sessionKey)).thenReturn(List.of(driver));

            adapter.getSessionByKey(sessionKey);
            adapter.getSessionByKey(sessionKey, true);

            verify(openF1Client, times(2)).getSessionByKey(sessionKey);
        }

        @Test
        @DisplayName("should return cached data on fallback when cache exists")
        void shouldReturnCachedDataOnFallback() {
            int sessionKey = 9472;
            EventWithDrivers cachedSession = new EventWithDrivers(
                sessionKey, "GP", "Race", "Monza", "Italy", "IT", null, null, 2024, List.of()
            );
            sessionKeyCache.put(sessionKey, Optional.of(cachedSession));

            Optional<EventWithDrivers> result = adapter.getSessionByKeyFallback(sessionKey, new RuntimeException("API Error"));

            assertTrue(result.isPresent());
            assertEquals(sessionKey, result.get().sessionKey());
        }

        @Test
        @DisplayName("should throw ExternalServiceUnavailableException on fallback when no cache exists")
        void shouldThrowExceptionOnFallbackWithNoCache() {
            int sessionKey = 99999;

            ExternalServiceUnavailableException ex = assertThrows(
                ExternalServiceUnavailableException.class,
                () -> adapter.getSessionByKeyFallback(sessionKey, new RuntimeException("API Error"))
            );

            assertEquals("OpenF1", ex.getServiceName());
            assertTrue(ex.getMessage().contains("temporarily unavailable"));
        }
    }
}
