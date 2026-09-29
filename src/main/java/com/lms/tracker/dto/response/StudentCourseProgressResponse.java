package com.lms.tracker.dto.response;

import com.lms.tracker.entity.CourseCompletionStatus;

import java.util.List;

/** One student's progress inside a course report. */
public record StudentCourseProgressResponse(
        Long studentId,
        String studentName,
        String studentEmail,
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
