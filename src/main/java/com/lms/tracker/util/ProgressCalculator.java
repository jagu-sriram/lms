package com.lms.tracker.util;

import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.AssessmentStatus;
import com.lms.tracker.entity.CourseCompletionStatus;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.ProgressStatus;
import com.lms.tracker.exception.BadRequestException;

import java.util.Collection;
import java.util.List;

/**
 * Pure calculation rules, kept free of Spring so they are trivially unit-testable.
 *
 * Course completion: every lesson and every assessment is one "item".
 * A lesson contributes its completion percentage, an assessment contributes 100 when PASSED, else 0.
 * completion = floor(sum / items). Flooring guarantees 100% only when everything is really done.
 */
public final class ProgressCalculator {

    private ProgressCalculator() {
    }

    /**
     * Validates / derives the lesson status from the percentage.
     * 0 -> NOT_STARTED, 1..99 -> IN_PROGRESS, 100 -> COMPLETED.
     * If the client also sends a status, it must agree with the percentage.
     */
    public static ProgressStatus resolveLessonStatus(int percentage, ProgressStatus requested) {
        if (percentage < 0 || percentage > 100) {
            throw new BadRequestException("completionPercentage must be between 0 and 100");
        }
        ProgressStatus derived;
        if (percentage == 0) {
            derived = ProgressStatus.NOT_STARTED;
        } else if (percentage == 100) {
            derived = ProgressStatus.COMPLETED;
        } else {
            derived = ProgressStatus.IN_PROGRESS;
        }
        if (requested != null && requested != derived) {
            throw new BadRequestException("status " + requested + " is inconsistent with completionPercentage "
                    + percentage + " (expected " + derived + ")");
        }
        return derived;
    }

    public static ProgressMetrics calculate(Collection<Progress> lessonProgress, Collection<AssessmentResult> results) {
        List<Integer> percentages = lessonProgress.stream().map(Progress::getCompletionPercentage).toList();
        int lessonsCompleted = (int) lessonProgress.stream()
                .filter(p -> p.getStatus() == ProgressStatus.COMPLETED)
                .count();
        int passed = (int) results.stream().filter(r -> r.getStatus() == AssessmentStatus.PASSED).count();
        boolean anyAttempt = results.stream().anyMatch(r -> r.getAttempts() > 0);
        return calculateFromValues(percentages, lessonsCompleted, passed, results.size(), anyAttempt);
    }

    public static ProgressMetrics calculateFromValues(List<Integer> lessonPercentages, int lessonsCompleted,
                                                      int assessmentsPassed, int totalAssessments,
                                                      boolean anyAssessmentAttempt) {
        int totalLessons = lessonPercentages.size();
        int items = totalLessons + totalAssessments;
        long sum = lessonPercentages.stream().mapToLong(Integer::longValue).sum() + 100L * assessmentsPassed;

        int percentage = items == 0 ? 0 : (int) (sum / items);

        CourseCompletionStatus status;
        if (items > 0 && lessonsCompleted == totalLessons && assessmentsPassed == totalAssessments) {
            status = CourseCompletionStatus.COMPLETED;
        } else if (sum == 0 && !anyAssessmentAttempt) {
            status = CourseCompletionStatus.NOT_STARTED;
        } else {
            status = CourseCompletionStatus.IN_PROGRESS;
        }
        return new ProgressMetrics(percentage, status, lessonsCompleted, totalLessons, assessmentsPassed, totalAssessments);
    }

    /** Rounded average of a list of percentages; 0 for an empty list. */
    public static int average(Collection<Integer> percentages) {
        if (percentages.isEmpty()) {
            return 0;
        }
        return (int) Math.round(percentages.stream().mapToInt(Integer::intValue).average().orElse(0));
    }
}
