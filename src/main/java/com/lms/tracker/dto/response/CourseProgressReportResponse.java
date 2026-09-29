package com.lms.tracker.dto.response;

import java.util.List;

/**
 * Response of GET /api/progress/course/{id}.
 *
 * @param scope SELF when a student views their own progress, ALL_STUDENTS for the owning instructor or an admin
 */
public record CourseProgressReportResponse(
        Long courseId,
        String courseTitle,
        Long instructorId,
        String instructorName,
        String scope,
        int enrolledStudents,
        int averageCompletionPercentage,
        List<StudentCourseProgressResponse> students
) {
    public static final String SCOPE_SELF = "SELF";
    public static final String SCOPE_ALL = "ALL_STUDENTS";
}
