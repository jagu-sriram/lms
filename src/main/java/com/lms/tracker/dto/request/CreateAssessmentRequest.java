package com.lms.tracker.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAssessmentRequest(
        @NotBlank(message = "is required")
        @Size(min = 3, max = 150, message = "must be 3-150 characters")
        String title,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String description,

        @NotNull(message = "is required")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 1000, message = "must be at most 1000")
        Integer maxScore,

        @NotNull(message = "is required")
        @Min(value = 0, message = "must be at least 0")
        @Max(value = 1000, message = "must be at most 1000")
        Integer passingScore,

        @NotNull(message = "is required")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 10, message = "must be at most 10")
        Integer maxAttempts
) {
}
