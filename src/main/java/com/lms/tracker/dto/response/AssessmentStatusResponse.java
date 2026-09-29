package com.lms.tracker.dto.response;

import com.lms.tracker.entity.AssessmentStatus;

import java.time.Instant;

/** A student's status for one assessment. resultId is what the grading endpoint expects. */
public record AssessmentStatusResponse(
        Long resultId,
        Long assessmentId,
        String assessmentTitle,
        Long courseId,
        String courseTitle,
        AssessmentStatus status,
        Integer score,
        int maxScore,
        int passingScore,
        int attempts,
        int maxAttempts,
        boolean completed,
        Instant lastAttemptAt,
        Instant gradedAt,
        String feedback
) {
}
