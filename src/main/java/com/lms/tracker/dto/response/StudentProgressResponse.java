package com.lms.tracker.dto.response;

import java.util.List;

/** Response of GET /api/progress/me. */
public record StudentProgressResponse(
        Long studentId,
        String studentName,
        int overallCompletionPercentage,
        int enrolledCourses,
        int completedCourses,
        List<CourseProgressResponse> courses
) {
}
