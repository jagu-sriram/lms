package com.lms.tracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * instructorId is only honoured for ADMIN callers. An INSTRUCTOR always becomes the owner
 * of the course they create; passing someone else's id is rejected with 403.
 */
public record CreateCourseRequest(
        @NotBlank(message = "is required")
        @Size(min = 3, max = 150, message = "must be 3-150 characters")
        String title,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String description,

        @Positive(message = "must be a positive number")
        Long instructorId
) {
}
