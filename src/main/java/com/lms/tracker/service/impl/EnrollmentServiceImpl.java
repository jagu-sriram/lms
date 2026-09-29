package com.lms.tracker.service.impl;

import com.lms.tracker.dto.response.EnrollmentResponse;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.Enrollment;
import com.lms.tracker.entity.EnrollmentStatus;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.exception.ConflictException;
import com.lms.tracker.exception.ResourceNotFoundException;
import com.lms.tracker.repository.CourseRepository;
import com.lms.tracker.repository.EnrollmentRepository;
import com.lms.tracker.repository.UserRepository;
import com.lms.tracker.security.AccessPolicy;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.EnrollmentService;
import com.lms.tracker.util.DtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentServiceImpl.class);

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final AccessPolicy accessPolicy;
    private final ProgressProvisioner provisioner;

    public EnrollmentServiceImpl(EnrollmentRepository enrollmentRepository,
                                 CourseRepository courseRepository,
                                 UserRepository userRepository,
                                 AccessPolicy accessPolicy,
                                 ProgressProvisioner provisioner) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
        this.provisioner = provisioner;
    }

    /** A student can only ever enroll themselves - the student id comes from the token, never the request. */
    @Override
    @Transactional
    public EnrollmentResponse enroll(Long courseId, AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "enroll in courses", RoleName.STUDENT);
        Course course = courseRepository.findWithInstructorById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        User student = userRepository.findById(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", actor.getId()));

        Optional<Enrollment> existing = enrollmentRepository.findByStudentAndCourse(student.getId(), courseId);
        Enrollment enrollment;
        if (existing.isPresent()) {
            enrollment = existing.get();
            if (enrollment.isActive()) {
                throw new ConflictException("You are already enrolled in this course");
            }
            enrollment.activate();
        } else {
            enrollment = new Enrollment(student, course);
        }
        enrollment = enrollmentRepository.saveAndFlush(enrollment);
        provisioner.provisionEnrollment(student, course);

        log.info("Enrollment created: enrollmentId={} studentId={} courseId={}", enrollment.getId(), student.getId(), courseId);
        return DtoMapper.toEnrollment(enrollment);
    }

    @Override
    @Transactional
    public void drop(Long courseId, AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "drop courses", RoleName.STUDENT);
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(actor.getId(), courseId)
                .filter(Enrollment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("No active enrollment found for course " + courseId));
        enrollment.drop();
        log.info("Enrollment dropped: enrollmentId={} studentId={} courseId={}", enrollment.getId(), actor.getId(), courseId);
    }

    @Override
    public List<EnrollmentResponse> myEnrollments(AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "view personal enrollments", RoleName.STUDENT);
        return enrollmentRepository.findByStudentWithCourse(actor.getId(), EnrollmentStatus.ACTIVE).stream()
                .map(DtoMapper::toEnrollment)
                .toList();
    }

    @Override
    public List<EnrollmentResponse> allEnrollments(AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "view all enrollments", RoleName.ADMIN);
        return enrollmentRepository.findAllWithStudentAndCourse().stream()
                .map(DtoMapper::toEnrollment)
                .toList();
    }
}
