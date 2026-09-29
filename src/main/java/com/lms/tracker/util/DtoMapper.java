package com.lms.tracker.util;

import com.lms.tracker.dto.response.AssessmentResponse;
import com.lms.tracker.dto.response.AssessmentStatusResponse;
import com.lms.tracker.dto.response.EnrollmentResponse;
import com.lms.tracker.dto.response.LessonProgressResponse;
import com.lms.tracker.dto.response.LessonResponse;
import com.lms.tracker.dto.response.ProgressRecordResponse;
import com.lms.tracker.dto.response.UserResponse;
import com.lms.tracker.entity.Assessment;
import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.Enrollment;
import com.lms.tracker.entity.Lesson;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.User;

/**
 * Entity -> DTO mapping. JPA entities never leave the service layer, which avoids
 * lazy-loading surprises, infinite JSON recursion and accidental data exposure.
 */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static UserResponse toUser(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRoleName(),
                user.isEnabled(), user.getCreatedAt());
    }

    public static LessonResponse toLesson(Lesson lesson, boolean includeContent) {
        return new LessonResponse(lesson.getId(), lesson.getTitle(), includeContent ? lesson.getContent() : null,
                lesson.getDisplayOrder(), lesson.getDurationMinutes());
    }

    public static AssessmentResponse toAssessment(Assessment a) {
        return new AssessmentResponse(a.getId(), a.getCourse().getId(), a.getTitle(), a.getDescription(),
                a.getMaxScore(), a.getPassingScore(), a.getMaxAttempts());
    }

    public static EnrollmentResponse toEnrollment(Enrollment e) {
        return new EnrollmentResponse(e.getId(), e.getStudent().getId(), e.getStudent().getFullName(),
                e.getCourse().getId(), e.getCourse().getTitle(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public static LessonProgressResponse toLessonProgress(Progress p) {
        return new LessonProgressResponse(p.getId(), p.getLesson().getId(), p.getLesson().getTitle(),
                p.getLesson().getDisplayOrder(), p.getStatus(), p.getCompletionPercentage(),
                p.getLastAccessedAt(), p.getCompletedAt());
    }

    public static ProgressRecordResponse toProgressRecord(Progress p) {
        return new ProgressRecordResponse(p.getId(), p.getStudent().getId(), p.getCourse().getId(),
                p.getLesson().getId(), p.getLesson().getTitle(), p.getStatus(), p.getCompletionPercentage(),
                p.getLastAccessedAt(), p.getCompletedAt(), p.getCreatedAt(), p.getUpdatedAt());
    }

    /**
     * @param course the course the assessment belongs to (passed in so callers control fetching)
     */
    public static AssessmentStatusResponse toAssessmentStatus(AssessmentResult r, Course course) {
        Assessment a = r.getAssessment();
        return new AssessmentStatusResponse(r.getId(), a.getId(), a.getTitle(), course.getId(), course.getTitle(),
                r.getStatus(), r.getScore(), a.getMaxScore(), a.getPassingScore(), r.getAttempts(),
                a.getMaxAttempts(), r.isCompleted(), r.getLastAttemptAt(), r.getGradedAt(), r.getFeedback());
    }
}
