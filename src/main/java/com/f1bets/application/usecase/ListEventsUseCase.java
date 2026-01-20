package com.f1bets.application.usecase;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.SessionQuery;
import com.f1bets.application.port.F1DataProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Use case for listing F1 events with driver markets.
 *
 * Note: No @Transactional here - this use case makes external HTTP calls
 * to the F1 data provider, which should not be done inside a transaction.
 */
@Service
public class ListEventsUseCase {

    private final F1DataProvider f1DataProvider;

    public ListEventsUseCase(F1DataProvider f1DataProvider) {
        this.f1DataProvider = f1DataProvider;
    }

    public List<EventWithDrivers> execute(String sessionType, Integer year, String countryCode) {
        return execute(sessionType, year, countryCode, false);
    }

    public List<EventWithDrivers> execute(String sessionType, Integer year, String countryCode, boolean skipCache) {
        SessionQuery query = SessionQuery.of(sessionType, year, countryCode);
        return f1DataProvider.getSessions(query, skipCache);
    }
}
