package com.f1bets.api.dto.response;

import org.slf4j.MDC;

import java.time.Instant;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    String requestId
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, MDC.get("requestId"));
    }
}
