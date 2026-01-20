package com.f1bets.domain.exception;

import com.f1bets.domain.model.SessionKey;

import java.time.Instant;

public class EventNotEndedException extends RuntimeException {

    private final SessionKey sessionKey;
    private final Instant dateEnd;
    private final Instant currentTime;

    public EventNotEndedException(SessionKey sessionKey, Instant dateEnd, Instant currentTime) {
        super(buildMessage(sessionKey, dateEnd));
        this.sessionKey = sessionKey;
        this.dateEnd = dateEnd;
        this.currentTime = currentTime;
    }

    private static String buildMessage(SessionKey sessionKey, Instant dateEnd) {
        if (dateEnd == null) {
            return "Event " + sessionKey.getValue() + " has not ended yet (end time not available)";
        }
        return "Event " + sessionKey.getValue() + " has not ended yet. Ends at " + dateEnd;
    }

    public SessionKey getSessionKey() {
        return sessionKey;
    }

    public Instant getDateEnd() {
        return dateEnd;
    }

    public Instant getCurrentTime() {
        return currentTime;
    }
}
