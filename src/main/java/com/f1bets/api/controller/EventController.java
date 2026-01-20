package com.f1bets.api.controller;

import com.f1bets.api.dto.request.SettleEventRequest;
import com.f1bets.api.dto.response.EventResponse;
import com.f1bets.api.dto.response.SettleEventResponse;
import com.f1bets.application.dto.SettleEventCommand;
import com.f1bets.application.usecase.ListEventsUseCase;
import com.f1bets.application.usecase.SettleEventUseCase;
import com.f1bets.domain.model.DriverNumber;
import com.f1bets.domain.model.SessionKey;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@Validated
@Tag(name = "Events", description = "Browse F1 events and settle race outcomes")
@RateLimiter(name = "api")
public class EventController {

    private final ListEventsUseCase listEventsUseCase;
    private final SettleEventUseCase settleEventUseCase;

    public EventController(ListEventsUseCase listEventsUseCase, SettleEventUseCase settleEventUseCase) {
        this.listEventsUseCase = listEventsUseCase;
        this.settleEventUseCase = settleEventUseCase;
    }

    @GetMapping
    @Operation(
        summary = "List F1 events with driver markets",
        description = "Retrieve F1 sessions from OpenF1 API with participating drivers and computed odds. Filter by session type, year, or country. Use Cache-Control: no-cache header to bypass cache."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
        @ApiResponse(responseCode = "503", description = "OpenF1 API unavailable - cached data returned if available")
    })
    public ResponseEntity<List<EventResponse>> listEvents(
            @Parameter(
                description = "Type of F1 session (e.g., Race, Qualifying, Sprint, Sprint Qualifying, Practice 1, Practice 2, Practice 3)",
                example = "Race"
            )
            @RequestParam(required = false) String sessionType,

            @Parameter(
                description = "Season year (e.g., 2023, 2024, 2025)",
                example = "2024"
            )
            @RequestParam(required = false) Integer year,

            @Parameter(
                description = "ISO 3-letter country code (e.g., GBR, ITA, MON, AUS, USA, JPN, BRA, NLD, ESP, MEX, SGP, BEL, SAU, BHR, ARE, QAT)",
                example = "GBR"
            )
            @RequestParam(required = false) String countryCode,

            @Parameter(description = "Cache control header - use 'no-cache' to bypass cache", hidden = true)
            @RequestHeader(value = "Cache-Control", required = false) String cacheControl) {

        boolean skipCache = "no-cache".equalsIgnoreCase(cacheControl);
        var events = listEventsUseCase.execute(sessionType, year, countryCode, skipCache);
        var response = events.stream()
            .map(EventResponse::from)
            .toList();

        return ResponseEntity.ok(response);
    }

    // TODO: In production, this endpoint requires admin authentication (OAuth2/JWT with admin role).
    //       Settlement is a privileged operation that should only be performed by authorized operators.
    //       For this take-home assignment, authentication is intentionally omitted for simplicity.
    @PostMapping("/{sessionKey}/settle")
    @Operation(
        summary = "Settle an event outcome",
        description = "Declare the winning driver for an F1 session. All pending bets are resolved: winning bets get payout credited, losing bets are marked as lost. Events can only be settled once. NOTE: In production, this would require admin authentication."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Event settled successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request (validation error)"),
        @ApiResponse(responseCode = "409", description = "Event already settled or idempotency key conflict")
    })
    public ResponseEntity<SettleEventResponse> settleEvent(
            @Parameter(
                description = "F1 session identifier from OpenF1 API",
                example = "9158",
                required = true
            )
            @PathVariable @Positive(message = "Session key must be positive") int sessionKey,

            @Parameter(
                description = "Unique request identifier (UUID) to prevent duplicate settlements",
                example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                required = true
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody SettleEventRequest request) {

        var command = new SettleEventCommand(
            SessionKey.of(sessionKey),
            DriverNumber.of(request.winningDriverNumber())
        );

        var result = settleEventUseCase.execute(command);
        return ResponseEntity.ok(SettleEventResponse.from(result));
    }
}
