package com.lms.tracker.util;

import com.lms.tracker.entity.CourseCompletionStatus;

/**
 * Aggregated numbers for one student in one course.
 */
public record ProgressMetrics(
        int completionPercentage,
        CourseCompletionStatus status,
        int lessonsCompleted,
        int totalLessons,
        int assessmentsPassed,
        int totalAssessments
) {
}
