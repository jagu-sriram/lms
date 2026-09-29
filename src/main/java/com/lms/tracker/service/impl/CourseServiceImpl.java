package com.lms.tracker.service.impl;

import com.lms.tracker.dto.request.CreateAssessmentRequest;
import com.lms.tracker.dto.request.CreateCourseRequest;
import com.lms.tracker.dto.request.CreateLessonRequest;
import com.lms.tracker.dto.response.AssessmentResponse;
import com.lms.tracker.dto.response.CourseDetailResponse;
import com.lms.tracker.dto.response.CourseSummaryResponse;
import com.lms.tracker.dto.response.LessonResponse;
import com.lms.tracker.entity.Assessment;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.EnrollmentStatus;
import com.lms.tracker.entity.Lesson;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.exception.BadRequestException;
import com.lms.tracker.exception.ForbiddenOperationException;
import com.lms.tracker.exception.ResourceNotFoundException;
import com.lms.tracker.repository.AssessmentRepository;
import com.lms.tracker.repository.CourseRepository;
import com.lms.tracker.repository.EnrollmentRepository;
import com.lms.tracker.repository.LessonRepository;
import com.lms.tracker.repository.UserRepository;
import com.lms.tracker.security.AccessPolicy;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.CourseService;
import com.lms.tracker.util.DtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseServiceImpl.class);

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final AssessmentRepository assessmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final AccessPolicy accessPolicy;
    private final ProgressProvisioner provisioner;

    public CourseServiceImpl(CourseRepository courseRepository,
                             LessonRepository lessonRepository,
                             AssessmentRepository assessmentRepository,
                             EnrollmentRepository enrollmentRepository,
                             UserRepository userRepository,
                             AccessPolicy accessPolicy,
                             ProgressProvisioner provisioner) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.assessmentRepository = assessmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
        this.provisioner = provisioner;
    }

    @Override
    public List<CourseSummaryResponse> listCourses(AuthenticatedUser actor) {
        List<Course> courses = courseRepository.findAllWithInstructor();
        Map<Long, Long> lessonCounts = toCountMap(lessonRepository.countLessonsPerCourse());
        Map<Long, Long> assessmentCounts = toCountMap(assessmentRepository.countAssessmentsPerCourse());
        Map<Long, Long> enrollmentCounts = toCountMap(enrollmentRepository.countPerCourse(EnrollmentStatus.ACTIVE));

        Set<Long> myCourseIds = actor.hasRole(RoleName.STUDENT)
                ? enrollmentRepository.findByStudentWithCourse(actor.getId(), EnrollmentStatus.ACTIVE).stream()
                    .map(e -> e.getCourse().getId())
                    .collect(Collectors.toSet())
                : Set.of();

        return courses.stream()
                .map(c -> new CourseSummaryResponse(
                        c.getId(),
                        c.getTitle(),
                        c.getDescription(),
                        c.getInstructor().getId(),
                        c.getInstructor().getFullName(),
                        lessonCounts.getOrDefault(c.getId(), 0L),
                        assessmentCounts.getOrDefault(c.getId(), 0L),
                        enrollmentCounts.getOrDefault(c.getId(), 0L),
                        actor.hasRole(RoleName.STUDENT) ? myCourseIds.contains(c.getId()) : null,
                        c.getCreatedAt()))
                .toList();
    }

    @Override
    public CourseDetailResponse getCourse(Long courseId, AuthenticatedUser actor) {
        Course course = loadCourse(courseId);
        return toDetail(course, actor);
    }

    @Override
    @Transactional
    public CourseDetailResponse createCourse(CreateCourseRequest request, AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "create courses", RoleName.INSTRUCTOR, RoleName.ADMIN);

        User instructor;
        if (actor.hasRole(RoleName.INSTRUCTOR)) {
            if (request.instructorId() != null && !request.instructorId().equals(actor.getId())) {
                log.warn("SECURITY Access denied: userId={} role={} action='create course for another instructor' targetInstructorId={}",
                        actor.getId(), actor.getRole(), request.instructorId());
                throw new ForbiddenOperationException("Instructors can only create courses they teach themselves");
            }
            instructor = userRepository.findById(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", actor.getId()));
        } else {
            if (request.instructorId() == null) {
                throw new BadRequestException("instructorId is required when an admin creates a course");
            }
            instructor = userRepository.findById(request.instructorId())
                    .orElseThrow(() -> new BadRequestException("instructorId does not refer to an existing user"));
            if (instructor.getRoleName() != RoleName.INSTRUCTOR) {
                throw new BadRequestException("instructorId must refer to an INSTRUCTOR account");
            }
        }

        Course course = courseRepository.save(new Course(request.title().trim(),
                blankToNull(request.description()), instructor));
        log.info("Course created: courseId={} instructorId={} by userId={} role={}",
                course.getId(), instructor.getId(), actor.getId(), actor.getRole());
        return toDetail(course, actor);
    }

    @Override
    @Transactional
    public LessonResponse addLesson(Long courseId, CreateLessonRequest request, AuthenticatedUser actor) {
        Course course = loadCourse(courseId);
        accessPolicy.assertCanManageCourse(actor, course);

        int nextOrder = lessonRepository.findMaxDisplayOrder(courseId) + 1;
        Lesson lesson = lessonRepository.save(new Lesson(course, request.title().trim(),
                blankToNull(request.content()), nextOrder, request.durationMinutes()));
        provisioner.provisionLesson(lesson);

        log.info("Lesson added: lessonId={} courseId={} by userId={}", lesson.getId(), courseId, actor.getId());
        return DtoMapper.toLesson(lesson, true);
    }

    @Override
    @Transactional
    public AssessmentResponse addAssessment(Long courseId, CreateAssessmentRequest request, AuthenticatedUser actor) {
        Course course = loadCourse(courseId);
        accessPolicy.assertCanManageCourse(actor, course);

        if (request.passingScore() > request.maxScore()) {
            throw new BadRequestException("passingScore cannot be greater than maxScore");
        }
        Assessment assessment = assessmentRepository.save(new Assessment(course, request.title().trim(),
                blankToNull(request.description()), request.maxScore(), request.passingScore(), request.maxAttempts()));
        provisioner.provisionAssessment(assessment);

        log.info("Assessment added: assessmentId={} courseId={} by userId={}", assessment.getId(), courseId, actor.getId());
        return DtoMapper.toAssessment(assessment);
    }

    // ------------------------------------------------------------------ helpers

    private Course loadCourse(Long courseId) {
        return courseRepository.findWithInstructorById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private CourseDetailResponse toDetail(Course course, AuthenticatedUser actor) {
        boolean canManage = accessPolicy.canManageCourse(actor, course);
        Boolean enrolled = actor.hasRole(RoleName.STUDENT)
                ? accessPolicy.isActivelyEnrolled(actor.getId(), course.getId())
                : null;
        // Lesson content is only visible to people who have access to the course material
        boolean includeContent = canManage || Boolean.TRUE.equals(enrolled);

        List<LessonResponse> lessons = lessonRepository.findByCourseId(course.getId()).stream()
                .map(l -> DtoMapper.toLesson(l, includeContent))
                .toList();
        List<AssessmentResponse> assessments = assessmentRepository.findByCourseId(course.getId()).stream()
                .map(DtoMapper::toAssessment)
                .toList();
        long enrolledStudents = enrollmentRepository.countByCourseAndStatus(course.getId(), EnrollmentStatus.ACTIVE);

        return new CourseDetailResponse(course.getId(), course.getTitle(), course.getDescription(),
                course.getInstructor().getId(), course.getInstructor().getFullName(), enrolledStudents,
                enrolled, canManage, lessons, assessments, course.getCreatedAt());
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
