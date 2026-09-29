package com.lms.tracker.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "is required")
        Boolean enabled
) {
}
