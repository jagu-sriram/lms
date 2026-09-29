package com.lms.tracker.dto.response;

import com.lms.tracker.entity.EnrollmentStatus;

import java.time.Instant;

public record EnrollmentResponse(
        Long id,
        Long studentId,
        String studentName,
        Long courseId,
        String courseTitle,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant updatedAt
) {
}
