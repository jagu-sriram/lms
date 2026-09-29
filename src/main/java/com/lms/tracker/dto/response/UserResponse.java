package com.lms.tracker.dto.response;

import com.lms.tracker.entity.RoleName;

import java.time.Instant;

/** Public view of a user - never contains the password hash. */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        RoleName role,
        boolean enabled,
        Instant createdAt
) {
}
