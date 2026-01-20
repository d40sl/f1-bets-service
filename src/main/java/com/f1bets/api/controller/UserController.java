package com.f1bets.api.controller;

import com.f1bets.api.dto.response.UserResponse;
import com.f1bets.application.usecase.GetUserUseCase;
import com.f1bets.domain.model.UserId;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "View user profiles, balances, and bet history")
@RateLimiter(name = "api")
public class UserController {

    private final GetUserUseCase getUserUseCase;

    public UserController(GetUserUseCase getUserUseCase) {
        this.getUserUseCase = getUserUseCase;
    }

    @GetMapping("/{userId}")
    @Operation(
        summary = "Get user profile with balance and bets",
        description = "Retrieve user profile including current EUR balance and all placed bets with their status (PENDING, WON, LOST)."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User profile retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> getUser(
            @Parameter(
                description = "Unique user identifier. Use the same ID you used when placing bets.",
                example = "john-doe-123",
                required = true
            )
            @PathVariable String userId) {
        var userWithBets = getUserUseCase.execute(UserId.of(userId));
        return ResponseEntity.ok(UserResponse.from(userWithBets));
    }
}
