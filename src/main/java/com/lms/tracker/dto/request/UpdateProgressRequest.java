package com.lms.tracker.dto.request;

import com.lms.tracker.entity.ProgressStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Only these two fields can be changed. The owner (student), course and lesson of a
 * progress record can never be changed through the API - unknown fields such as
 * "studentId" are rejected with 400.
 *
 * status is optional; if omitted it is derived from the percentage
 * (0 = NOT_STARTED, 1-99 = IN_PROGRESS, 100 = COMPLETED). If it is supplied it must agree.
 */
public record UpdateProgressRequest(
        @NotNull(message = "is required")
        @Min(value = 0, message = "must be between 0 and 100")
        @Max(value = 100, message = "must be between 0 and 100")
        Integer completionPercentage,

        ProgressStatus status
) {
}
