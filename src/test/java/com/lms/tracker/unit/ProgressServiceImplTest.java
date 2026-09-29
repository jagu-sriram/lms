package com.lms.tracker.unit;

import com.lms.tracker.dto.request.UpdateProgressRequest;
import com.lms.tracker.dto.response.ProgressRecordResponse;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.EnrollmentStatus;
import com.lms.tracker.entity.Lesson;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.ProgressStatus;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.exception.BadRequestException;
import com.lms.tracker.exception.ForbiddenOperationException;
import com.lms.tracker.exception.ResourceNotFoundException;
import com.lms.tracker.repository.AssessmentResultRepository;
import com.lms.tracker.repository.CourseRepository;
import com.lms.tracker.repository.EnrollmentRepository;
import com.lms.tracker.repository.ProgressRepository;
import com.lms.tracker.security.AccessPolicy;
import com.lms.tracker.service.impl.ProgressReportAssembler;
import com.lms.tracker.service.impl.ProgressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer authorization is tested here WITHOUT any web layer or @PreAuthorize,
 * proving the ownership rule holds even if a controller annotation were forgotten.
 */
class ProgressServiceImplTest {

    private ProgressRepository progressRepository;
    private ProgressServiceImpl service;

    private User studentA;
    private User studentB;
    private User instructor;
    private Progress progressOfA;

    @BeforeEach
    void setUp() {
        progressRepository = mock(ProgressRepository.class);
        EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
        service = new ProgressServiceImpl(progressRepository, enrollmentRepository, mock(CourseRepository.class),
                mock(AssessmentResultRepository.class), new AccessPolicy(enrollmentRepository), new ProgressReportAssembler());

        instructor = TestEntities.user(1, RoleName.INSTRUCTOR);
        studentA = TestEntities.user(10, RoleName.STUDENT);
        studentB = TestEntities.user(11, RoleName.STUDENT);
        Course course = TestEntities.course(100, instructor);
        Lesson lesson = TestEntities.lesson(1000, course, 1);
        progressOfA = TestEntities.progress(25, studentA, lesson);

        when(progressRepository.findDetailedById(25L)).thenReturn(Optional.of(progressOfA));
        when(progressRepository.findDetailedById(404L)).thenReturn(Optional.empty());
        when(progressRepository.saveAndFlush(any(Progress.class))).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentRepository.existsByStudentAndCourseAndStatus(10L, 100L, EnrollmentStatus.ACTIVE)).thenReturn(true);
        when(enrollmentRepository.existsByStudentAndCourseAndStatus(11L, 100L, EnrollmentStatus.ACTIVE)).thenReturn(true);
    }

    @Test
    void ownerUpdatesProgress() {
        ProgressRecordResponse response = service.updateProgress(25L, new UpdateProgressRequest(40, null),
                TestEntities.principal(studentA));

        assertThat(response.completionPercentage()).isEqualTo(40);
        assertThat(response.status()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(progressOfA.getLastAccessedAt()).isNotNull();
        verify(progressRepository).saveAndFlush(progressOfA);
    }

    @Test
    void otherStudentIsRejectedAndNothingIsSaved() {
        assertThatThrownBy(() -> service.updateProgress(25L, new UpdateProgressRequest(100, null),
                TestEntities.principal(studentB)))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(progressOfA.getCompletionPercentage()).isZero();
        verify(progressRepository, never()).saveAndFlush(any());
    }

    @Test
    void instructorIsRejectedAndNothingIsSaved() {
        assertThatThrownBy(() -> service.updateProgress(25L, new UpdateProgressRequest(100, null),
                TestEntities.principal(instructor)))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(progressRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownRecordIs404() {
        assertThatThrownBy(() -> service.updateProgress(404L, new UpdateProgressRequest(10, null),
                TestEntities.principal(studentA)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void authorizationIsCheckedBeforeInputRules() {
        // even an invalid combination must not reveal anything to a non-owner: 403 wins
        assertThatThrownBy(() -> service.updateProgress(25L, new UpdateProgressRequest(50, ProgressStatus.COMPLETED),
                TestEntities.principal(studentB)))
                .isInstanceOf(ForbiddenOperationException.class);
        // the owner gets the validation error
        assertThatThrownBy(() -> service.updateProgress(25L, new UpdateProgressRequest(50, ProgressStatus.COMPLETED),
                TestEntities.principal(studentA)))
                .isInstanceOf(BadRequestException.class);
    }
}
