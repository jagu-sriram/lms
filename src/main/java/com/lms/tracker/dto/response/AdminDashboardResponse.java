package com.lms.tracker.dto.response;

public record AdminDashboardResponse(
        long totalUsers,
        long students,
        long instructors,
        long admins,
        long totalCourses,
        long totalLessons,
        long totalAssessments,
        long activeEnrollments,
        long droppedEnrollments,
        long lessonsCompleted,
        long progressRecords,
        long assessmentsPassed,
        long submissionsAwaitingGrading
) {
}
