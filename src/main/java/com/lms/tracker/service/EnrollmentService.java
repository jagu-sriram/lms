package com.lms.tracker.service;

import com.lms.tracker.dto.response.EnrollmentResponse;
import com.lms.tracker.security.AuthenticatedUser;

import java.util.List;

public interface EnrollmentService {

    EnrollmentResponse enroll(Long courseId, AuthenticatedUser actor);

    void drop(Long courseId, AuthenticatedUser actor);

    List<EnrollmentResponse> myEnrollments(AuthenticatedUser actor);

    List<EnrollmentResponse> allEnrollments(AuthenticatedUser actor);
}
