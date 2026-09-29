package com.lms.tracker.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.tracker.entity.Assessment;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.Enrollment;
import com.lms.tracker.entity.Lesson;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.repository.AssessmentRepository;
import com.lms.tracker.repository.AssessmentResultRepository;
import com.lms.tracker.repository.CourseRepository;
import com.lms.tracker.repository.EnrollmentRepository;
import com.lms.tracker.repository.LessonRepository;
import com.lms.tracker.repository.ProgressRepository;
import com.lms.tracker.repository.RevokedTokenRepository;
import com.lms.tracker.repository.RoleRepository;
import com.lms.tracker.repository.UserRepository;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.security.JwtService;
import com.lms.tracker.service.impl.ProgressProvisioner;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Base class for full-stack integration tests: real Spring context, real security filter chain,
 * real services and an in-memory H2 database. Requests go through MockMvc exactly like HTTP calls.
 *
 * Tests are deliberately NOT @Transactional, so lazy-loading or transaction bugs are not hidden;
 * instead the database is wiped before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "Passw0rd!";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected CourseRepository courseRepository;
    @Autowired protected LessonRepository lessonRepository;
    @Autowired protected AssessmentRepository assessmentRepository;
    @Autowired protected EnrollmentRepository enrollmentRepository;
    @Autowired protected ProgressRepository progressRepository;
    @Autowired protected AssessmentResultRepository assessmentResultRepository;
    @Autowired protected RevokedTokenRepository revokedTokenRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;
    @Autowired protected ProgressProvisioner provisioner;
    @Autowired private PlatformTransactionManager transactionManager;

    protected TransactionTemplate tx;

    @BeforeEach
    void cleanDatabase() {
        tx = new TransactionTemplate(transactionManager);
        assessmentResultRepository.deleteAllInBatch();
        progressRepository.deleteAllInBatch();
        enrollmentRepository.deleteAllInBatch();
        assessmentRepository.deleteAllInBatch();
        lessonRepository.deleteAllInBatch();
        courseRepository.deleteAllInBatch();
        revokedTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // ------------------------------------------------------------------ fixtures

    protected User createUser(String name, String email, RoleName role) {
        return userRepository.save(new User(name, email, passwordEncoder.encode(PASSWORD),
                roleRepository.findByName(role).orElseThrow()));
    }

    protected Course createCourse(User instructor, String title) {
        return courseRepository.save(new Course(title, "Description of " + title, instructor));
    }

    protected Lesson addLesson(Course course, String title, int order) {
        return lessonRepository.save(new Lesson(course, title, "Content of " + title, order, 30));
    }

    protected Assessment addAssessment(Course course, String title, int maxScore, int passingScore, int maxAttempts) {
        return assessmentRepository.save(new Assessment(course, title, null, maxScore, passingScore, maxAttempts));
    }

    /** Enrolls and provisions progress rows, like the real enrollment service. */
    protected void enroll(User student, Course course) {
        tx.executeWithoutResult(status -> {
            enrollmentRepository.save(new Enrollment(student, course));
            provisioner.provisionEnrollment(student, course);
        });
    }

    protected void drop(User student, Course course) {
        tx.executeWithoutResult(status ->
                enrollmentRepository.findByStudentAndCourse(student.getId(), course.getId()).orElseThrow().drop());
    }

    protected List<Progress> progressOf(User student, Course course) {
        return progressRepository.findByStudentAndCourse(student.getId(), course.getId());
    }

    protected Progress reloadProgress(Long id) {
        return progressRepository.findById(id).orElseThrow();
    }

    // ------------------------------------------------------------------ HTTP helpers

    /** A valid "Bearer ..." header value for the user, exactly as the login endpoint would issue. */
    protected String bearer(User user) {
        return "Bearer " + jwtService.issue(AuthenticatedUser.from(user)).token();
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
