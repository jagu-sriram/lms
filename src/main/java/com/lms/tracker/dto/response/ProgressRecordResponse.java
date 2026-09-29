package com.lms.tracker.dto.response;

import com.lms.tracker.entity.ProgressStatus;

import java.time.Instant;

/** Full progress record, returned by PUT /api/progress/{id}. */
public record ProgressRecordResponse(
        Long id,
        Long studentId,
        Long courseId,
        Long lessonId,
        String lessonTitle,
        ProgressStatus status,
        int completionPercentage,
        Instant lastAccessedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
