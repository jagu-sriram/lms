package com.lms.tracker.dto.response;

import com.lms.tracker.entity.CourseCompletionStatus;

import java.util.List;

/** The calling student's progress in one course. */
public record CourseProgressResponse(
        Long courseId,
        String courseTitle,
        String instructorName,
        int completionPercentage,
        CourseCompletionStatus status,
        int lessonsCompleted,
        int totalLessons,
        int assessmentsPassed,
        int totalAssessments,
        List<LessonProgressResponse> lessons,
        List<AssessmentStatusResponse> assessments
) {
}
