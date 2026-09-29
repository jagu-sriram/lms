package com.lms.tracker.service.impl;

import com.lms.tracker.dto.response.CourseProgressResponse;
import com.lms.tracker.dto.response.StudentCourseProgressResponse;
import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.util.DtoMapper;
import com.lms.tracker.util.ProgressCalculator;
import com.lms.tracker.util.ProgressMetrics;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds progress DTOs from already-loaded entities (no database access here).
 */
@Component
public class ProgressReportAssembler {

    public CourseProgressResponse courseProgress(Course course, List<Progress> progress, List<AssessmentResult> results) {
        ProgressMetrics m = ProgressCalculator.calculate(progress, results);
        return new CourseProgressResponse(
                course.getId(),
                course.getTitle(),
                course.getInstructor().getFullName(),
                m.completionPercentage(),
                m.status(),
                m.lessonsCompleted(),
                m.totalLessons(),
                m.assessmentsPassed(),
                m.totalAssessments(),
                progress.stream().map(DtoMapper::toLessonProgress).toList(),
                results.stream().map(r -> DtoMapper.toAssessmentStatus(r, course)).toList());
    }

    public StudentCourseProgressResponse studentCourseProgress(Long studentId, String studentName, String studentEmail,
                                                               Course course, List<Progress> progress,
                                                               List<AssessmentResult> results) {
        ProgressMetrics m = ProgressCalculator.calculate(progress, results);
        return new StudentCourseProgressResponse(
                studentId,
                studentName,
                studentEmail,
                m.completionPercentage(),
                m.status(),
                m.lessonsCompleted(),
                m.totalLessons(),
                m.assessmentsPassed(),
                m.totalAssessments(),
                progress.stream().map(DtoMapper::toLessonProgress).toList(),
                results.stream().map(r -> DtoMapper.toAssessmentStatus(r, course)).toList());
    }
}
