package com.f1bets.domain.exception;

public class ExternalServiceUnavailableException extends RuntimeException {

    private final String serviceName;

    public ExternalServiceUnavailableException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    public ExternalServiceUnavailableException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
