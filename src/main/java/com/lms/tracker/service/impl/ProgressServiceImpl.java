package com.lms.tracker.service.impl;

import com.lms.tracker.dto.request.UpdateProgressRequest;
import com.lms.tracker.dto.response.CourseProgressReportResponse;
import com.lms.tracker.dto.response.CourseProgressResponse;
import com.lms.tracker.dto.response.ProgressRecordResponse;
import com.lms.tracker.dto.response.StudentCourseProgressResponse;
import com.lms.tracker.dto.response.StudentProgressResponse;
import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.CourseCompletionStatus;
import com.lms.tracker.entity.Enrollment;
import com.lms.tracker.entity.EnrollmentStatus;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.ProgressStatus;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.exception.ResourceNotFoundException;
import com.lms.tracker.repository.AssessmentResultRepository;
import com.lms.tracker.repository.CourseRepository;
import com.lms.tracker.repository.EnrollmentRepository;
import com.lms.tracker.repository.ProgressRepository;
import com.lms.tracker.security.AccessPolicy;
import com.lms.tracker.security.AccessPolicy.CourseProgressScope;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.ProgressService;
import com.lms.tracker.util.DtoMapper;
import com.lms.tracker.util.ProgressCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Progress APIs. Every method receives the authenticated principal explicitly and performs
 * its own authorization through {@link AccessPolicy} - the controller annotations are only
 * the first, coarse layer.
 */
@Service
@Transactional(readOnly = true)
public class ProgressServiceImpl implements ProgressService {

    private static final Logger log = LoggerFactory.getLogger(ProgressServiceImpl.class);

    private final ProgressRepository progressRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final AccessPolicy accessPolicy;
    private final ProgressReportAssembler assembler;

    public ProgressServiceImpl(ProgressRepository progressRepository,
                               EnrollmentRepository enrollmentRepository,
                               CourseRepository courseRepository,
                               AssessmentResultRepository assessmentResultRepository,
                               AccessPolicy accessPolicy,
                               ProgressReportAssembler assembler) {
        this.progressRepository = progressRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.assessmentResultRepository = assessmentResultRepository;
        this.accessPolicy = accessPolicy;
        this.assembler = assembler;
    }

    // ------------------------------------------------------------------ GET /api/progress/me

    @Override
    public StudentProgressResponse getMyProgress(AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "view personal progress", RoleName.STUDENT);
        Long studentId = actor.getId(); // from the verified token - there is no id parameter to tamper with

        List<Enrollment> enrollments = enrollmentRepository.findByStudentWithCourse(studentId, EnrollmentStatus.ACTIVE);
        Map<Long, List<Progress>> progressByCourse = progressRepository.findByStudentWithLesson(studentId).stream()
                .collect(Collectors.groupingBy(p -> p.getCourse().getId()));
        Map<Long, List<AssessmentResult>> resultsByCourse = assessmentResultRepository.findByStudentWithAssessment(studentId).stream()
                .collect(Collectors.groupingBy(r -> r.getAssessment().getCourse().getId()));

        List<CourseProgressResponse> courses = enrollments.stream()
                .map(Enrollment::getCourse)
                .map(course -> assembler.courseProgress(course,
                        progressByCourse.getOrDefault(course.getId(), List.of()),
                        resultsByCourse.getOrDefault(course.getId(), List.of())))
                .toList();

        int overall = ProgressCalculator.average(courses.stream().map(CourseProgressResponse::completionPercentage).toList());
        int completed = (int) courses.stream().filter(c -> c.status() == CourseCompletionStatus.COMPLETED).count();

        log.info("Progress viewed: userId={} scope=SELF courses={}", studentId, courses.size());
        return new StudentProgressResponse(studentId, actor.getFullName(), overall, courses.size(), completed, courses);
    }

    // ------------------------------------------------------------------ GET /api/progress/course/{id}

    @Override
    public CourseProgressReportResponse getCourseProgress(Long courseId, AuthenticatedUser actor) {
        Course course = courseRepository.findWithInstructorById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

        // Throws 403 unless: student enrolled / instructor owns the course / admin
        CourseProgressScope scope = accessPolicy.resolveCourseProgressScope(actor, course);

        List<StudentCourseProgressResponse> students;
        int enrolledStudents;
        if (scope == CourseProgressScope.SELF) {
            // A student only ever sees their own row, even though other students are enrolled
            List<Progress> progress = progressRepository.findByStudentAndCourse(actor.getId(), courseId);
            List<AssessmentResult> results = assessmentResultRepository.findByStudentAndCourse(actor.getId(), courseId);
            students = List.of(assembler.studentCourseProgress(actor.getId(), actor.getFullName(), actor.getEmail(),
                    course, progress, results));
            enrolledStudents = (int) enrollmentRepository.countByCourseAndStatus(courseId, EnrollmentStatus.ACTIVE);
        } else {
            List<Enrollment> enrollments = enrollmentRepository.findByCourseWithStudent(courseId, EnrollmentStatus.ACTIVE);
            Map<Long, List<Progress>> progressByStudent = progressRepository.findByCourses(List.of(courseId)).stream()
                    .collect(Collectors.groupingBy(p -> p.getStudent().getId()));
            Map<Long, List<AssessmentResult>> resultsByStudent = assessmentResultRepository.findByCourses(List.of(courseId)).stream()
                    .collect(Collectors.groupingBy(r -> r.getStudent().getId()));

            students = enrollments.stream()
                    .map(Enrollment::getStudent)
                    .map(s -> assembler.studentCourseProgress(s.getId(), s.getFullName(), s.getEmail(), course,
                            progressByStudent.getOrDefault(s.getId(), List.of()),
                            resultsByStudent.getOrDefault(s.getId(), List.of())))
                    .toList();
            enrolledStudents = students.size();
        }

        int average = ProgressCalculator.average(
                students.stream().map(StudentCourseProgressResponse::completionPercentage).toList());
        User instructor = course.getInstructor();

        log.info("Course progress viewed: userId={} role={} courseId={} scope={} students={}",
                actor.getId(), actor.getRole(), courseId, scope, students.size());
        return new CourseProgressReportResponse(course.getId(), course.getTitle(), instructor.getId(),
                instructor.getFullName(), scope.name(), enrolledStudents, average, students);
    }

    // ------------------------------------------------------------------ PUT /api/progress/{id}

    @Override
    @Transactional
    public ProgressRecordResponse updateProgress(Long progressId, UpdateProgressRequest request, AuthenticatedUser actor) {
        // 1. Load the record from the database - the id alone proves nothing
        Progress progress = progressRepository.findDetailedById(progressId)
                .orElseThrow(() -> new ResourceNotFoundException("Progress record", progressId));

        // 2. Verify the caller may modify THIS record (owner + active enrollment, or admin)
        accessPolicy.assertCanUpdateProgress(actor, progress);

        // 3. Validate the business rules of the new values
        ProgressStatus newStatus = ProgressCalculator.resolveLessonStatus(request.completionPercentage(), request.status());
        ProgressStatus oldStatus = progress.getStatus();
        int oldPercentage = progress.getCompletionPercentage();

        // 4. Apply - only percentage/status/timestamps can change; owner, course and lesson are immutable
        progress.applyUpdate(request.completionPercentage(), newStatus, Instant.now());
        progress = progressRepository.saveAndFlush(progress);

        log.info("Progress updated: progressId={} studentId={} lessonId={} {}%/{} -> {}%/{} by userId={} role={}",
                progress.getId(), progress.getStudent().getId(), progress.getLesson().getId(),
                oldPercentage, oldStatus, progress.getCompletionPercentage(), newStatus,
                actor.getId(), actor.getRole());
        return DtoMapper.toProgressRecord(progress);
    }
}
