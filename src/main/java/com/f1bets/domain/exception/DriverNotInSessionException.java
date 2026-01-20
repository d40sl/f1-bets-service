package com.f1bets.domain.exception;

import com.f1bets.domain.model.DriverNumber;
import com.f1bets.domain.model.SessionKey;

public class DriverNotInSessionException extends RuntimeException {

    private final SessionKey sessionKey;
    private final DriverNumber driverNumber;

    public DriverNotInSessionException(SessionKey sessionKey, DriverNumber driverNumber) {
        super("Driver " + driverNumber.getValue() + " did not participate in session " + sessionKey.getValue());
        this.sessionKey = sessionKey;
        this.driverNumber = driverNumber;
    }

    public SessionKey getSessionKey() {
        return sessionKey;
    }

    public DriverNumber getDriverNumber() {
        return driverNumber;
    }
}
