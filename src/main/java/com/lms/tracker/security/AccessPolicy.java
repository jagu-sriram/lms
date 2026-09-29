package com.lms.tracker.security;

import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.EnrollmentStatus;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.exception.ForbiddenOperationException;
import com.lms.tracker.repository.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Central object-level authorization rules (IDOR / BOLA protection).
 *
 * Role annotations on controllers only answer "may this kind of user call this endpoint?".
 * The methods here answer "may THIS user act on THIS object?" and are called from the
 * service layer after the object has been loaded from the database - so a user-supplied
 * id is never trusted on its own.
 *
 * Business rules:
 * <ul>
 *   <li>STUDENT - may read and update only their own progress, and only while actively enrolled.</li>
 *   <li>INSTRUCTOR - may read progress of students in courses they teach; lesson progress is read-only
 *       for instructors; they may grade assessments of their own courses.</li>
 *   <li>ADMIN - may read everything and may correct progress / grade (audited in the logs).</li>
 * </ul>
 */
@Component
public class AccessPolicy {

    private static final Logger log = LoggerFactory.getLogger(AccessPolicy.class);

    /** What part of a course's progress the caller may see. */
    public enum CourseProgressScope {
        /** A student sees only their own row. */
        SELF,
        /** The owning instructor or an admin sees every enrolled student. */
        ALL_STUDENTS
    }

    private final EnrollmentRepository enrollmentRepository;

    public AccessPolicy(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    // ------------------------------------------------------------------ generic

    public void requireRole(AuthenticatedUser actor, String action, RoleName... allowed) {
        if (actor == null) {
            throw new ForbiddenOperationException("Authentication is required");
        }
        boolean ok = Arrays.stream(allowed).anyMatch(actor::hasRole);
        if (!ok) {
            deny(actor, action, "role", null, "You do not have permission to " + action);
        }
    }

    public boolean isActivelyEnrolled(Long studentId, Long courseId) {
        return enrollmentRepository.existsByStudentAndCourseAndStatus(studentId, courseId, EnrollmentStatus.ACTIVE);
    }

    // ------------------------------------------------------------------ courses

    /** Owner instructor or admin may add lessons / assessments and see all students. */
    public boolean canManageCourse(AuthenticatedUser actor, Course course) {
        return actor.hasRole(RoleName.ADMIN)
                || (actor.hasRole(RoleName.INSTRUCTOR) && course.isOwnedBy(actor.getId()));
    }

    public void assertCanManageCourse(AuthenticatedUser actor, Course course) {
        if (!canManageCourse(actor, course)) {
            deny(actor, "manage course", "Course", course.getId(),
                    "You are not authorized to manage this course");
        }
    }

    // ------------------------------------------------------------------ progress (read)

    /**
     * GET /api/progress/course/{id}
     *
     * @throws ForbiddenOperationException unless the student is enrolled, the instructor owns the course, or the caller is admin
     */
    public CourseProgressScope resolveCourseProgressScope(AuthenticatedUser actor, Course course) {
        switch (actor.getRole()) {
            case ADMIN:
                return CourseProgressScope.ALL_STUDENTS;
            case INSTRUCTOR:
                if (course.isOwnedBy(actor.getId())) {
                    return CourseProgressScope.ALL_STUDENTS;
                }
                deny(actor, "view course progress", "Course", course.getId(),
                        "You are not authorized to view progress for this course");
                break;
            case STUDENT:
                if (isActivelyEnrolled(actor.getId(), course.getId())) {
                    return CourseProgressScope.SELF;
                }
                deny(actor, "view course progress", "Course", course.getId(),
                        "You are not enrolled in this course");
                break;
            default:
                break;
        }
        deny(actor, "view course progress", "Course", course.getId(),
                "You are not authorized to view progress for this course");
        return null; // unreachable - deny always throws
    }

    // ------------------------------------------------------------------ progress (write)

    /**
     * PUT /api/progress/{id} - called with the record already loaded from the database.
     */
    public void assertCanUpdateProgress(AuthenticatedUser actor, Progress progress) {
        switch (actor.getRole()) {
            case ADMIN:
                log.info("AUDIT Admin progress correction: adminId={} progressId={} studentId={}",
                        actor.getId(), progress.getId(), progress.getStudent().getId());
                return;
            case STUDENT:
                if (!progress.belongsTo(actor.getId())) {
                    deny(actor, "update progress", "Progress", progress.getId(),
                            "You are not authorized to modify this progress record");
                }
                if (!isActivelyEnrolled(actor.getId(), progress.getCourse().getId())) {
                    deny(actor, "update progress", "Progress", progress.getId(),
                            "You are not actively enrolled in this course");
                }
                return;
            case INSTRUCTOR:
            default:
                deny(actor, "update progress", "Progress", progress.getId(),
                        "Instructors have read-only access to student lesson progress");
        }
    }

    // ------------------------------------------------------------------ assessments

    /** Student submitting an attempt for their own assessment result. */
    public void assertCanSubmitAttempt(AuthenticatedUser actor, AssessmentResult result) {
        if (!actor.hasRole(RoleName.STUDENT) || !result.belongsTo(actor.getId())) {
            deny(actor, "submit assessment attempt", "AssessmentResult", result.getId(),
                    "You are not authorized to submit this assessment");
        }
        Long courseId = result.getAssessment().getCourse().getId();
        if (!isActivelyEnrolled(actor.getId(), courseId)) {
            deny(actor, "submit assessment attempt", "AssessmentResult", result.getId(),
                    "You are not actively enrolled in this course");
        }
    }

    /** Grading is allowed for the instructor who owns the course, and for admins. */
    public void assertCanGrade(AuthenticatedUser actor, AssessmentResult result) {
        Course course = result.getAssessment().getCourse();
        boolean allowed = actor.hasRole(RoleName.ADMIN)
                || (actor.hasRole(RoleName.INSTRUCTOR) && course.isOwnedBy(actor.getId()));
        if (!allowed) {
            deny(actor, "grade assessment", "AssessmentResult", result.getId(),
                    "You are not authorized to grade this assessment");
        }
    }

    // ------------------------------------------------------------------ helper

    private void deny(AuthenticatedUser actor, String action, String resourceType, Object resourceId, String message) {
        log.warn("SECURITY Access denied: userId={} role={} action='{}' resource={} resourceId={}",
                actor.getId(), actor.getRole(), action, resourceType, resourceId);
        throw new ForbiddenOperationException(message);
    }
}
