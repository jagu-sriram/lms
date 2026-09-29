package com.lms.tracker.service.impl;

import com.lms.tracker.dto.request.GradeAssessmentRequest;
import com.lms.tracker.dto.response.AssessmentStatusResponse;
import com.lms.tracker.entity.Assessment;
import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.AssessmentStatus;
import com.lms.tracker.entity.EnrollmentStatus;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.exception.BadRequestException;
import com.lms.tracker.exception.ConflictException;
import com.lms.tracker.exception.ForbiddenOperationException;
import com.lms.tracker.exception.ResourceNotFoundException;
import com.lms.tracker.repository.AssessmentRepository;
import com.lms.tracker.repository.AssessmentResultRepository;
import com.lms.tracker.repository.EnrollmentRepository;
import com.lms.tracker.repository.UserRepository;
import com.lms.tracker.security.AccessPolicy;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.AssessmentService;
import com.lms.tracker.util.DtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AssessmentServiceImpl implements AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentServiceImpl.class);

    private final AssessmentRepository assessmentRepository;
    private final AssessmentResultRepository resultRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final AccessPolicy accessPolicy;

    public AssessmentServiceImpl(AssessmentRepository assessmentRepository,
                                 AssessmentResultRepository resultRepository,
                                 EnrollmentRepository enrollmentRepository,
                                 UserRepository userRepository,
                                 AccessPolicy accessPolicy) {
        this.assessmentRepository = assessmentRepository;
        this.resultRepository = resultRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public List<AssessmentStatusResponse> myAssessments(AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "view personal assessments", RoleName.STUDENT);
        Set<Long> activeCourseIds = enrollmentRepository.findByStudentWithCourse(actor.getId(), EnrollmentStatus.ACTIVE)
                .stream()
                .map(e -> e.getCourse().getId())
                .collect(Collectors.toSet());
        return resultRepository.findByStudentWithAssessment(actor.getId()).stream()
                .filter(r -> activeCourseIds.contains(r.getAssessment().getCourse().getId()))
                .map(r -> DtoMapper.toAssessmentStatus(r, r.getAssessment().getCourse()))
                .toList();
    }

    @Override
    @Transactional
    public AssessmentStatusResponse submitAttempt(Long assessmentId, AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "submit assessments", RoleName.STUDENT);
        Assessment assessment = assessmentRepository.findWithCourseById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment", assessmentId));

        if (!accessPolicy.isActivelyEnrolled(actor.getId(), assessment.getCourse().getId())) {
            log.warn("SECURITY Access denied: userId={} role={} action='submit assessment' resource=Assessment resourceId={}",
                    actor.getId(), actor.getRole(), assessmentId);
            throw new ForbiddenOperationException("You are not enrolled in the course for this assessment");
        }

        // The result row is looked up by (assessment, authenticated student) - never by a client-supplied result id
        AssessmentResult result = resultRepository.findByAssessmentAndStudent(assessmentId, actor.getId())
                .orElseGet(() -> resultRepository.save(new AssessmentResult(assessment,
                        userRepository.getReferenceById(actor.getId()))));
        accessPolicy.assertCanSubmitAttempt(actor, result);

        if (result.getStatus() == AssessmentStatus.PASSED) {
            throw new ConflictException("You have already passed this assessment");
        }
        if (result.getStatus() == AssessmentStatus.SUBMITTED) {
            throw new ConflictException("Your previous attempt is still awaiting grading");
        }
        if (!result.hasAttemptsRemaining()) {
            throw new ConflictException("No attempts remaining (maximum " + assessment.getMaxAttempts() + ")");
        }

        result.recordAttempt(Instant.now());
        result = resultRepository.saveAndFlush(result);
        log.info("Assessment attempt submitted: resultId={} assessmentId={} studentId={} attempt={}",
                result.getId(), assessmentId, actor.getId(), result.getAttempts());
        return DtoMapper.toAssessmentStatus(result, assessment.getCourse());
    }

    @Override
    @Transactional
    public AssessmentStatusResponse grade(Long resultId, GradeAssessmentRequest request, AuthenticatedUser actor) {
        AssessmentResult result = resultRepository.findDetailedById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment result", resultId));

        // Only the instructor who owns the course (or an admin) may grade
        accessPolicy.assertCanGrade(actor, result);

        Assessment assessment = result.getAssessment();
        if (result.getStatus() != AssessmentStatus.SUBMITTED) {
            throw new ConflictException("Only a submitted attempt can be graded (current status: " + result.getStatus() + ")");
        }
        if (request.score() > assessment.getMaxScore()) {
            throw new BadRequestException("score cannot be greater than maxScore (" + assessment.getMaxScore() + ")");
        }

        User grader = userRepository.getReferenceById(actor.getId());
        String feedback = request.feedback() == null || request.feedback().isBlank() ? null : request.feedback().trim();
        result.grade(request.score(), feedback, grader, Instant.now());
        result = resultRepository.saveAndFlush(result);

        log.info("AUDIT Assessment graded: resultId={} studentId={} score={} status={} by userId={} role={}",
                result.getId(), result.getStudent().getId(), result.getScore(), result.getStatus(),
                actor.getId(), actor.getRole());
        return DtoMapper.toAssessmentStatus(result, assessment.getCourse());
    }
}
