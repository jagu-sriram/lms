package com.lms.tracker.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * @param enrolled  whether the current STUDENT is actively enrolled (null for other roles)
 * @param canManage whether the current user may add lessons/assessments and view all student progress
 */
public record CourseDetailResponse(
        Long id,
        String title,
        String description,
        Long instructorId,
        String instructorName,
        long enrolledStudents,
        Boolean enrolled,
        boolean canManage,
        List<LessonResponse> lessons,
        List<AssessmentResponse> assessments,
        Instant createdAt
) {
}
