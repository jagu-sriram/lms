package com.lms.tracker.dto.response;

public record InstructorCourseSummary(
        Long courseId,
        String title,
        int lessonCount,
        int assessmentCount,
        int enrolledStudents,
        int averageCompletionPercentage,
        int studentsCompleted,
        int submissionsAwaitingGrading
) {
}
