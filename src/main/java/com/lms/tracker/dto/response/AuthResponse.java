package com.lms.tracker.dto.response;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user
) {
    @Override
    public String toString() {
        return "AuthResponse[tokenType=" + tokenType + ", expiresAt=" + expiresAt + ", accessToken=****]";
    }
}
