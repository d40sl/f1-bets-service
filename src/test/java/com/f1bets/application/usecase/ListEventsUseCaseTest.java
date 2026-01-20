package com.f1bets.application.usecase;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.SessionQuery;
import com.f1bets.application.port.F1DataProvider;
import com.f1bets.domain.exception.ExternalServiceUnavailableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListEventsUseCaseTest {

    @Mock
    private F1DataProvider f1DataProvider;

    private ListEventsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListEventsUseCase(f1DataProvider);
    }

    @Nested
    @DisplayName("Event listing")
    class EventListing {

        @Test
        @DisplayName("should return events with no filters")
        void shouldReturnEventsWithNoFilters() {
            List<EventWithDrivers> events = List.of(createEvent(9158, "Race", 2024, "GBR"));
            when(f1DataProvider.getSessions(any(SessionQuery.class), eq(false))).thenReturn(events);

            List<EventWithDrivers> result = useCase.execute(null, null, null);

            assertEquals(1, result.size());
            assertEquals(9158, result.get(0).sessionKey());
        }

        @Test
        @DisplayName("should filter by session type")
        void shouldFilterBySessionType() {
            List<EventWithDrivers> events = List.of(createEvent(9158, "Race", 2024, "GBR"));
            when(f1DataProvider.getSessions(any(SessionQuery.class), eq(false))).thenReturn(events);

            List<EventWithDrivers> result = useCase.execute("Race", null, null);

            assertEquals(1, result.size());
            verify(f1DataProvider).getSessions(
                argThat(q -> "Race".equals(q.sessionType())),
                eq(false)
            );
        }

        @Test
        @DisplayName("should filter by year")
        void shouldFilterByYear() {
            List<EventWithDrivers> events = List.of(createEvent(9158, "Race", 2024, "GBR"));
            when(f1DataProvider.getSessions(any(SessionQuery.class), eq(false))).thenReturn(events);

            List<EventWithDrivers> result = useCase.execute(null, 2024, null);

            assertEquals(1, result.size());
            verify(f1DataProvider).getSessions(
                argThat(q -> Integer.valueOf(2024).equals(q.year())),
                eq(false)
            );
        }

        @Test
        @DisplayName("should filter by country code")
        void shouldFilterByCountryCode() {
            List<EventWithDrivers> events = List.of(createEvent(9158, "Race", 2024, "GBR"));
            when(f1DataProvider.getSessions(any(SessionQuery.class), eq(false))).thenReturn(events);

            List<EventWithDrivers> result = useCase.execute(null, null, "GBR");

            assertEquals(1, result.size());
            verify(f1DataProvider).getSessions(
                argThat(q -> "GBR".equals(q.countryCode())),
                eq(false)
            );
        }

        @Test
        @DisplayName("should combine multiple filters")
        void shouldCombineMultipleFilters() {
            List<EventWithDrivers> events = List.of(createEvent(9158, "Race", 2024, "GBR"));
            when(f1DataProvider.getSessions(any(SessionQuery.class), eq(false))).thenReturn(events);

            List<EventWithDrivers> result = useCase.execute("Race", 2024, "GBR");

            assertEquals(1, result.size());
            verify(f1DataProvider).getSessions(
                argThat(q -> "Race".equals(q.sessionType()) 
                    && Integer.valueOf(2024).equals(q.year())
                    && "GBR".equals(q.countryCode())),
                eq(false)
            );
        }

        @Test
        @DisplayName("should bypass cache when skipCache is true")
        void shouldBypassCache() {
            List<EventWithDrivers> events = List.of(createEvent(9158, "Race", 2024, "GBR"));
            when(f1DataProvider.getSessions(any(SessionQuery.class), eq(true))).thenReturn(events);

            List<EventWithDrivers> result = useCase.execute(null, null, null, true);

            assertEquals(1, result.size());
            verify(f1DataProvider).getSessions(any(SessionQuery.class), eq(true));
        }

        @Test
        @DisplayName("should return empty list when no events match")
        void shouldReturnEmptyWhenNoEvents() {
            when(f1DataProvider.getSessions(any(SessionQuery.class), eq(false))).thenReturn(List.of());

            List<EventWithDrivers> result = useCase.execute("Sprint", 2025, "XYZ");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should propagate ExternalServiceUnavailableException when F1 API fails")
        void shouldPropagateExternalServiceException() {
            when(f1DataProvider.getSessions(any(SessionQuery.class), eq(false)))
                .thenThrow(new ExternalServiceUnavailableException("OpenF1", "Service unavailable"));

            ExternalServiceUnavailableException ex = assertThrows(
                ExternalServiceUnavailableException.class,
                () -> useCase.execute("Race", 2024, null)
            );

            assertEquals("OpenF1", ex.getServiceName());
        }
    }

    private EventWithDrivers createEvent(int sessionKey, String sessionType, int year, String countryCode) {
        return new EventWithDrivers(
            sessionKey,
            sessionType,
            sessionType,
            "Circuit",
            "Country",
            countryCode,
            Instant.now(),
            Instant.now().plusSeconds(7200),
            year,
            List.of()
        );
    }
}
