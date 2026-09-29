package com.lms.tracker.dto.response;

import com.lms.tracker.entity.ProgressStatus;

import java.time.Instant;

/** One lesson's progress for one student. progressId is what PUT /api/progress/{id} expects. */
public record LessonProgressResponse(
        Long progressId,
        Long lessonId,
        String lessonTitle,
        int lessonOrder,
        ProgressStatus status,
        int completionPercentage,
        Instant lastAccessedAt,
        Instant completedAt
) {
}
