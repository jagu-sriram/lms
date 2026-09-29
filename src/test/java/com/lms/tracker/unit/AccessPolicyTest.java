package com.lms.tracker.unit;

import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.EnrollmentStatus;
import com.lms.tracker.entity.Lesson;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.exception.ForbiddenOperationException;
import com.lms.tracker.repository.EnrollmentRepository;
import com.lms.tracker.security.AccessPolicy;
import com.lms.tracker.security.AccessPolicy.CourseProgressScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessPolicyTest {

    private EnrollmentRepository enrollmentRepository;
    private AccessPolicy policy;

    private User instructorA;
    private User instructorB;
    private User studentA;
    private User studentB;
    private User admin;
    private Course courseA;
    private Progress progressOfA;

    @BeforeEach
    void setUp() {
        enrollmentRepository = mock(EnrollmentRepository.class);
        policy = new AccessPolicy(enrollmentRepository);

        instructorA = TestEntities.user(1, RoleName.INSTRUCTOR);
        instructorB = TestEntities.user(2, RoleName.INSTRUCTOR);
        studentA = TestEntities.user(10, RoleName.STUDENT);
        studentB = TestEntities.user(11, RoleName.STUDENT);
        admin = TestEntities.user(99, RoleName.ADMIN);
        courseA = TestEntities.course(100, instructorA);
        Lesson lesson = TestEntities.lesson(1000, courseA, 1);
        progressOfA = TestEntities.progress(5000, studentA, lesson);

        when(enrollmentRepository.existsByStudentAndCourseAndStatus(10L, 100L, EnrollmentStatus.ACTIVE)).thenReturn(true);
    }

    @Test
    void studentCanUpdateOwnProgressWhileEnrolled() {
        assertThatCode(() -> policy.assertCanUpdateProgress(TestEntities.principal(studentA), progressOfA))
                .doesNotThrowAnyException();
    }

    @Test
    void studentCannotUpdateSomeoneElsesProgress() {
        when(enrollmentRepository.existsByStudentAndCourseAndStatus(11L, 100L, EnrollmentStatus.ACTIVE)).thenReturn(true);
        assertThatThrownBy(() -> policy.assertCanUpdateProgress(TestEntities.principal(studentB), progressOfA))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("You are not authorized to modify this progress record");
    }

    @Test
    void studentCannotUpdateAfterDroppingCourse() {
        when(enrollmentRepository.existsByStudentAndCourseAndStatus(10L, 100L, EnrollmentStatus.ACTIVE)).thenReturn(false);
        assertThatThrownBy(() -> policy.assertCanUpdateProgress(TestEntities.principal(studentA), progressOfA))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void instructorsNeverModifyLessonProgressEvenInOwnCourse() {
        assertThatThrownBy(() -> policy.assertCanUpdateProgress(TestEntities.principal(instructorA), progressOfA))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void adminMayCorrectProgress() {
        assertThatCode(() -> policy.assertCanUpdateProgress(TestEntities.principal(admin), progressOfA))
                .doesNotThrowAnyException();
    }

    @Test
    void courseProgressScopes() {
        assertThat(policy.resolveCourseProgressScope(TestEntities.principal(studentA), courseA))
                .isEqualTo(CourseProgressScope.SELF);
        assertThat(policy.resolveCourseProgressScope(TestEntities.principal(instructorA), courseA))
                .isEqualTo(CourseProgressScope.ALL_STUDENTS);
        assertThat(policy.resolveCourseProgressScope(TestEntities.principal(admin), courseA))
                .isEqualTo(CourseProgressScope.ALL_STUDENTS);

        assertThatThrownBy(() -> policy.resolveCourseProgressScope(TestEntities.principal(studentB), courseA))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> policy.resolveCourseProgressScope(TestEntities.principal(instructorB), courseA))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void onlyOwnerOrAdminManagesCourse() {
        assertThat(policy.canManageCourse(TestEntities.principal(instructorA), courseA)).isTrue();
        assertThat(policy.canManageCourse(TestEntities.principal(admin), courseA)).isTrue();
        assertThat(policy.canManageCourse(TestEntities.principal(instructorB), courseA)).isFalse();
        assertThat(policy.canManageCourse(TestEntities.principal(studentA), courseA)).isFalse();
    }
}
