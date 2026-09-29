package com.lms.tracker.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GradeAssessmentRequest(
        @NotNull(message = "is required")
        @Min(value = 0, message = "must be at least 0")
        @Max(value = 1000, message = "must be at most 1000")
        Integer score,

        @Size(max = 500, message = "must be at most 500 characters")
        String feedback
) {
}
