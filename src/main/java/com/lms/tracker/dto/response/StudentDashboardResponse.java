package com.lms.tracker.dto.response;

import java.util.List;

public record StudentDashboardResponse(
        String studentName,
        int enrolledCourses,
        int completedCourses,
        int overallCompletionPercentage,
        int lessonsCompleted,
        int totalLessons,
        int pendingAssessments,
        int passedAssessments,
        List<CourseProgressResponse> courses
) {
}
