package com.f1bets.application.port;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.SessionQuery;

import java.util.List;
import java.util.Optional;

public interface F1DataProvider {

    List<EventWithDrivers> getSessions(SessionQuery query);

    default List<EventWithDrivers> getSessions(SessionQuery query, boolean skipCache) {
        return getSessions(query);
    }

    Optional<EventWithDrivers> getSessionByKey(int sessionKey);

    default Optional<EventWithDrivers> getSessionByKey(int sessionKey, boolean skipCache) {
        return getSessionByKey(sessionKey);
    }
}
