package com.lms.tracker.config;

import com.lms.tracker.entity.Assessment;
import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.Enrollment;
import com.lms.tracker.entity.Lesson;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.ProgressStatus;
import com.lms.tracker.entity.Role;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.repository.AssessmentRepository;
import com.lms.tracker.repository.AssessmentResultRepository;
import com.lms.tracker.repository.CourseRepository;
import com.lms.tracker.repository.EnrollmentRepository;
import com.lms.tracker.repository.LessonRepository;
import com.lms.tracker.repository.ProgressRepository;
import com.lms.tracker.repository.RoleRepository;
import com.lms.tracker.repository.UserRepository;
import com.lms.tracker.service.impl.ProgressProvisioner;
import com.lms.tracker.util.ProgressCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 1. Always makes sure the three roles exist.
 * 2. When app.seed.demo-data=true and the database has no users yet, creates demo users,
 *    courses, lessons, assessments, enrollments and some progress so every dashboard has data.
 *    Passwords are BCrypt-hashed exactly like real registrations.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    public static final String DEMO_ADMIN_PASSWORD = "Admin@123";
    public static final String DEMO_INSTRUCTOR_PASSWORD = "Instructor@123";
    public static final String DEMO_STUDENT_PASSWORD = "Student@123";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final AssessmentRepository assessmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProgressRepository progressRepository;
    private final AssessmentResultRepository resultRepository;
    private final ProgressProvisioner provisioner;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedDemoData;

    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           CourseRepository courseRepository,
                           LessonRepository lessonRepository,
                           AssessmentRepository assessmentRepository,
                           EnrollmentRepository enrollmentRepository,
                           ProgressRepository progressRepository,
                           AssessmentResultRepository resultRepository,
                           ProgressProvisioner provisioner,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.seed.demo-data:false}") boolean seedDemoData) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.assessmentRepository = assessmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
        this.resultRepository = resultRepository;
        this.provisioner = provisioner;
        this.passwordEncoder = passwordEncoder;
        this.seedDemoData = seedDemoData;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (RoleName name : RoleName.values()) {
            if (roleRepository.findByName(name).isEmpty()) {
                roleRepository.save(new Role(name));
                log.info("Created role {}", name);
            }
        }
        if (seedDemoData && userRepository.count() == 0) {
            seedDemoData();
        }
    }

    private void seedDemoData() {
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        Role instructorRole = roleRepository.findByName(RoleName.INSTRUCTOR).orElseThrow();
        Role studentRole = roleRepository.findByName(RoleName.STUDENT).orElseThrow();

        String adminHash = passwordEncoder.encode(DEMO_ADMIN_PASSWORD);
        String instructorHash = passwordEncoder.encode(DEMO_INSTRUCTOR_PASSWORD);
        String studentHash = passwordEncoder.encode(DEMO_STUDENT_PASSWORD);

        userRepository.save(new User("System Admin", "admin@lms.local", adminHash, adminRole));
        User alice = userRepository.save(new User("Alice Johnson", "alice.instructor@lms.local", instructorHash, instructorRole));
        User bob = userRepository.save(new User("Bob Smith", "bob.instructor@lms.local", instructorHash, instructorRole));
        User ravi = userRepository.save(new User("Ravi Kumar", "student1@lms.local", studentHash, studentRole));
        User priya = userRepository.save(new User("Priya Sharma", "student2@lms.local", studentHash, studentRole));
        User arjun = userRepository.save(new User("Arjun Reddy", "student3@lms.local", studentHash, studentRole));

        // ---- Alice: Java Fundamentals
        Course java = courseRepository.save(new Course("Java Fundamentals",
                "Core Java: syntax, OOP, collections, exceptions and streams.", alice));
        lessons(java,
                "Introduction to Java and the JVM",
                "Variables, Types and Operators",
                "Object-Oriented Programming",
                "Collections Framework",
                "Exceptions and Streams");
        Assessment javaQuiz = assessmentRepository.save(new Assessment(java, "OOP Quiz",
                "Classes, inheritance, polymorphism and interfaces.", 100, 60, 3));
        Assessment javaFinal = assessmentRepository.save(new Assessment(java, "Final Exam",
                "Covers the whole course.", 100, 70, 2));

        // ---- Alice: Spring Boot REST APIs
        Course spring = courseRepository.save(new Course("Spring Boot REST APIs",
                "Build secure REST APIs with Spring Boot, Spring Data JPA and Spring Security.", alice));
        lessons(spring,
                "Spring Boot Project Setup",
                "Controllers and DTOs",
                "Persistence with Spring Data JPA",
                "Securing APIs with Spring Security");
        assessmentRepository.save(new Assessment(spring, "REST API Project",
                "Build a CRUD API with validation and error handling.", 100, 65, 2));

        // ---- Bob: React for Beginners
        Course react = courseRepository.save(new Course("React for Beginners",
                "Components, props, state, hooks and calling REST APIs.", bob));
        lessons(react,
                "JSX and Components",
                "Props and State",
                "Hooks: useState and useEffect",
                "Fetching Data from an API");
        Assessment reactQuiz = assessmentRepository.save(new Assessment(react, "React Basics Quiz",
                "Components, props and hooks.", 50, 30, 3));

        // ---- Enrollments (progress rows are provisioned automatically)
        enroll(ravi, java);
        enroll(ravi, spring);
        enroll(priya, java);
        enroll(priya, react);
        enroll(arjun, react);

        Instant now = Instant.now();

        // Ravi: good progress in Java, just started Spring
        setProgress(ravi, java, 100, 100, 100, 50, 0);
        grade(ravi, javaQuiz, 85, "Great understanding of OOP.", alice, now);
        submit(ravi, javaFinal, now);
        setProgress(ravi, spring, 40, 0, 0, 0);

        // Priya: early in Java (failed the quiz once), completed React entirely
        setProgress(priya, java, 100, 30, 0, 0, 0);
        grade(priya, javaQuiz, 40, "Review inheritance and interfaces, then retry.", alice, now);
        setProgress(priya, react, 100, 100, 100, 100);
        grade(priya, reactQuiz, 45, "Excellent work!", bob, now);

        // Arjun: enrolled in React, not started yet

        log.info("Demo data created: 6 users, 3 courses, 5 enrollments. See README for credentials.");
    }

    private void lessons(Course course, String... titles) {
        int order = 1;
        for (String title : titles) {
            lessonRepository.save(new Lesson(course, title,
                    "Lesson content for \"" + title + "\". Read the notes, watch the video and try the exercises.",
                    order++, 30 + (order * 5)));
        }
    }

    private void enroll(User student, Course course) {
        enrollmentRepository.save(new Enrollment(student, course));
        provisioner.provisionEnrollment(student, course);
    }

    private void setProgress(User student, Course course, int... percentages) {
        List<Progress> rows = progressRepository.findByStudentAndCourse(student.getId(), course.getId());
        Instant now = Instant.now();
        for (int i = 0; i < rows.size() && i < percentages.length; i++) {
            if (percentages[i] > 0) {
                ProgressStatus status = ProgressCalculator.resolveLessonStatus(percentages[i], null);
                rows.get(i).applyUpdate(percentages[i], status, now);
            }
        }
    }

    private void submit(User student, Assessment assessment, Instant now) {
        AssessmentResult result = resultRepository.findByAssessmentAndStudent(assessment.getId(), student.getId()).orElseThrow();
        result.recordAttempt(now);
    }

    private void grade(User student, Assessment assessment, int score, String feedback, User grader, Instant now) {
        AssessmentResult result = resultRepository.findByAssessmentAndStudent(assessment.getId(), student.getId()).orElseThrow();
        result.recordAttempt(now);
        result.grade(score, feedback, grader, now);
    }
}
