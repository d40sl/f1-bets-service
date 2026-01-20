package com.f1bets.domain.exception;

import com.f1bets.domain.model.UserId;

public class UserNotFoundException extends RuntimeException {

    private final UserId userId;

    public UserNotFoundException(UserId userId) {
        super("User not found: id=" + userId);
        this.userId = userId;
    }

    public UserId getUserId() {
        return userId;
    }
}
