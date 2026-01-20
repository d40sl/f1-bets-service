package com.f1bets.domain.model;

import java.util.Objects;

/**
 * User ID value object.
 * Non-blank, max 100 characters.
 */
public final class UserId {

    private static final int MAX_LENGTH = 100;

    private final String value;

    private UserId(String value) {
        this.value = value;
    }

    public static UserId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "User ID exceeds maximum length of " + MAX_LENGTH + " characters"
            );
        }
        // Basic validation: alphanumeric, hyphens, and underscores only
        if (!trimmed.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException(
                "User ID contains invalid characters. Only alphanumeric, hyphens, and underscores allowed"
            );
        }
        return new UserId(trimmed);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserId userId)) return false;
        return Objects.equals(value, userId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
