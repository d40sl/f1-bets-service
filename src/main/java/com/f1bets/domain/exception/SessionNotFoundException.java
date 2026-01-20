package com.f1bets.domain.exception;

import com.f1bets.domain.model.SessionKey;

public class SessionNotFoundException extends RuntimeException {

    private final SessionKey sessionKey;

    public SessionNotFoundException(SessionKey sessionKey) {
        super("Session not found: sessionKey=" + sessionKey.getValue());
        this.sessionKey = sessionKey;
    }

    public SessionKey getSessionKey() {
        return sessionKey;
    }
}
