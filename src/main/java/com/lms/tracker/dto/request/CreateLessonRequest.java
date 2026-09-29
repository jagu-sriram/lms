package com.lms.tracker.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLessonRequest(
        @NotBlank(message = "is required")
        @Size(min = 3, max = 150, message = "must be 3-150 characters")
        String title,

        @Size(max = 5000, message = "must be at most 5000 characters")
        String content,

        @NotNull(message = "is required")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 600, message = "must be at most 600")
        Integer durationMinutes
) {
}
