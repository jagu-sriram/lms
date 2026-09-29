package com.lms.tracker.dto.response;

public record AssessmentResponse(
        Long id,
        Long courseId,
        String title,
        String description,
        int maxScore,
        int passingScore,
        int maxAttempts
) {
}
