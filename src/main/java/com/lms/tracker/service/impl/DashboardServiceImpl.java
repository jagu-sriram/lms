package com.lms.tracker.service.impl;

import com.lms.tracker.dto.response.AdminDashboardResponse;
import com.lms.tracker.dto.response.AssessmentStatusResponse;
import com.lms.tracker.dto.response.CourseProgressResponse;
import com.lms.tracker.dto.response.InstructorCourseSummary;
import com.lms.tracker.dto.response.InstructorDashboardResponse;
import com.lms.tracker.dto.response.StudentDashboardResponse;
import com.lms.tracker.dto.response.StudentProgressResponse;
import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.AssessmentStatus;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.CourseCompletionStatus;
import com.lms.tracker.entity.EnrollmentStatus;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.ProgressStatus;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.repository.AssessmentRepository;
import com.lms.tracker.repository.AssessmentResultRepository;
import com.lms.tracker.repository.CourseRepository;
import com.lms.tracker.repository.EnrollmentRepository;
import com.lms.tracker.repository.LessonRepository;
import com.lms.tracker.repository.ProgressRepository;
import com.lms.tracker.repository.UserRepository;
import com.lms.tracker.security.AccessPolicy;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.DashboardService;
import com.lms.tracker.service.ProgressService;
import com.lms.tracker.util.ProgressCalculator;
import com.lms.tracker.util.ProgressMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);

    private final ProgressService progressService;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final AssessmentRepository assessmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProgressRepository progressRepository;
    private final AssessmentResultRepository resultRepository;
    private final UserRepository userRepository;
    private final AccessPolicy accessPolicy;

    public DashboardServiceImpl(ProgressService progressService,
                                CourseRepository courseRepository,
                                LessonRepository lessonRepository,
                                AssessmentRepository assessmentRepository,
                                EnrollmentRepository enrollmentRepository,
                                ProgressRepository progressRepository,
                                AssessmentResultRepository resultRepository,
                                UserRepository userRepository,
                                AccessPolicy accessPolicy) {
        this.progressService = progressService;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.assessmentRepository = assessmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public StudentDashboardResponse studentDashboard(AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "view the student dashboard", RoleName.STUDENT);
        StudentProgressResponse progress = progressService.getMyProgress(actor);

        int lessonsCompleted = 0;
        int totalLessons = 0;
        int pending = 0;
        int passed = 0;
        for (CourseProgressResponse course : progress.courses()) {
            lessonsCompleted += course.lessonsCompleted();
            totalLessons += course.totalLessons();
            for (AssessmentStatusResponse a : course.assessments()) {
                if (a.status() == AssessmentStatus.PASSED) {
                    passed++;
                } else if (!a.completed()) {
                    pending++;
                }
            }
        }
        return new StudentDashboardResponse(progress.studentName(), progress.enrolledCourses(),
                progress.completedCourses(), progress.overallCompletionPercentage(), lessonsCompleted,
                totalLessons, pending, passed, progress.courses());
    }

    @Override
    public InstructorDashboardResponse instructorDashboard(AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "view the instructor dashboard", RoleName.INSTRUCTOR);

        // Only courses this instructor teaches - derived from the token, not from a parameter
        List<Course> courses = courseRepository.findByInstructorId(actor.getId());
        if (courses.isEmpty()) {
            return new InstructorDashboardResponse(actor.getFullName(), 0, 0, 0, List.of());
        }
        List<Long> courseIds = courses.stream().map(Course::getId).toList();

        Map<Long, Long> lessonCounts = toCountMap(lessonRepository.countLessonsPerCourse());
        Map<Long, Long> assessmentCounts = toCountMap(assessmentRepository.countAssessmentsPerCourse());

        Map<Long, Set<Long>> activeStudentsByCourse = enrollmentRepository
                .findByCoursesWithStudent(courseIds, EnrollmentStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(e -> e.getCourse().getId(),
                        Collectors.mapping(e -> e.getStudent().getId(), Collectors.toSet())));
        Map<Long, Map<Long, List<Progress>>> progress = progressRepository.findByCourses(courseIds).stream()
                .collect(Collectors.groupingBy(p -> p.getCourse().getId(),
                        Collectors.groupingBy(p -> p.getStudent().getId())));
        Map<Long, Map<Long, List<AssessmentResult>>> results = resultRepository.findByCourses(courseIds).stream()
                .collect(Collectors.groupingBy(r -> r.getAssessment().getCourse().getId(),
                        Collectors.groupingBy(r -> r.getStudent().getId())));

        List<InstructorCourseSummary> summaries = new ArrayList<>();
        int totalStudents = 0;
        int totalAwaiting = 0;
        for (Course course : courses) {
            Set<Long> students = activeStudentsByCourse.getOrDefault(course.getId(), Set.of());
            Map<Long, List<Progress>> courseProgress = progress.getOrDefault(course.getId(), Map.of());
            Map<Long, List<AssessmentResult>> courseResults = results.getOrDefault(course.getId(), Map.of());

            List<Integer> percentages = new ArrayList<>();
            int completed = 0;
            int awaiting = 0;
            for (Long studentId : students) {
                List<AssessmentResult> studentResults = courseResults.getOrDefault(studentId, List.of());
                ProgressMetrics m = ProgressCalculator.calculate(courseProgress.getOrDefault(studentId, List.of()), studentResults);
                percentages.add(m.completionPercentage());
                if (m.status() == CourseCompletionStatus.COMPLETED) {
                    completed++;
                }
                awaiting += (int) studentResults.stream().filter(r -> r.getStatus() == AssessmentStatus.SUBMITTED).count();
            }
            summaries.add(new InstructorCourseSummary(course.getId(), course.getTitle(),
                    lessonCounts.getOrDefault(course.getId(), 0L).intValue(),
                    assessmentCounts.getOrDefault(course.getId(), 0L).intValue(),
                    students.size(), ProgressCalculator.average(percentages), completed, awaiting));
            totalStudents += students.size();
            totalAwaiting += awaiting;
        }

        log.info("Instructor dashboard viewed: userId={} courses={}", actor.getId(), courses.size());
        return new InstructorDashboardResponse(actor.getFullName(), courses.size(), totalStudents, totalAwaiting, summaries);
    }

    @Override
    public AdminDashboardResponse adminDashboard(AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "view the admin dashboard", RoleName.ADMIN);
        return new AdminDashboardResponse(
                userRepository.count(),
                userRepository.countByRoleName(RoleName.STUDENT),
                userRepository.countByRoleName(RoleName.INSTRUCTOR),
                userRepository.countByRoleName(RoleName.ADMIN),
                courseRepository.count(),
                lessonRepository.count(),
                assessmentRepository.count(),
                enrollmentRepository.countByStatus(EnrollmentStatus.ACTIVE),
                enrollmentRepository.countByStatus(EnrollmentStatus.DROPPED),
                progressRepository.countByStatus(ProgressStatus.COMPLETED),
                progressRepository.count(),
                resultRepository.countByStatus(AssessmentStatus.PASSED),
                resultRepository.countByStatus(AssessmentStatus.SUBMITTED));
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row -> ((Number) row[1]).longValue()));
    }
}
