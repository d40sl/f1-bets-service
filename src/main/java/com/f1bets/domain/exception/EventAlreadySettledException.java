package com.f1bets.domain.exception;

import com.f1bets.domain.model.SessionKey;

public class EventAlreadySettledException extends RuntimeException {

    private final SessionKey sessionKey;

    public EventAlreadySettledException(SessionKey sessionKey) {
        super("Event already settled: sessionKey=" + sessionKey.getValue());
        this.sessionKey = sessionKey;
    }

    public SessionKey getSessionKey() {
        return sessionKey;
    }
}
