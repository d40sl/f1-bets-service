package com.f1bets.api.controller;

import com.f1bets.api.dto.request.PlaceBetRequest;
import com.f1bets.api.dto.response.BetResponse;
import com.f1bets.application.dto.PlaceBetCommand;
import com.f1bets.application.usecase.PlaceBetUseCase;
import com.f1bets.domain.model.DriverNumber;
import com.f1bets.domain.model.Money;
import com.f1bets.domain.model.SessionKey;
import com.f1bets.domain.model.UserId;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bets")
@Tag(name = "Bets", description = "Place and manage bets on F1 race outcomes")
@RateLimiter(name = "api")
public class BetController {

    private final PlaceBetUseCase placeBetUseCase;

    public BetController(PlaceBetUseCase placeBetUseCase) {
        this.placeBetUseCase = placeBetUseCase;
    }

    @PostMapping
    @Operation(
        summary = "Place a bet on a driver to win an event",
        description = "Place a bet on a specific driver to win an F1 session. New users automatically receive EUR 100.00 starting balance. Odds are computed server-side based on session and driver."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Bet placed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or insufficient balance"),
        @ApiResponse(responseCode = "409", description = "Idempotency key conflict - same key used with different request body")
    })
    public ResponseEntity<BetResponse> placeBet(
            @Parameter(
                description = "Unique user identifier. Alphanumeric, hyphens, underscores allowed. Max 100 chars.",
                example = "john-doe-123",
                required = true
            )
            @RequestHeader("X-User-Id") String userIdHeader,

            @Parameter(
                description = "Unique request identifier (UUID) to prevent duplicate bets. Reusing with same request returns cached response. Reusing with different request returns 409.",
                example = "550e8400-e29b-41d4-a716-446655440000",
                required = true
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody PlaceBetRequest request) {

        var command = new PlaceBetCommand(
            UserId.of(userIdHeader),
            SessionKey.of(request.sessionKey()),
            DriverNumber.of(request.driverNumber()),
            Money.fromDecimalForStake(request.amount()),
            idempotencyKey
        );

        var result = placeBetUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(BetResponse.from(result));
    }
}
