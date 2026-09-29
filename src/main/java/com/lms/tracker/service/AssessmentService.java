package com.lms.tracker.service;

import com.lms.tracker.dto.request.GradeAssessmentRequest;
import com.lms.tracker.dto.response.AssessmentStatusResponse;
import com.lms.tracker.security.AuthenticatedUser;

import java.util.List;

public interface AssessmentService {

    List<AssessmentStatusResponse> myAssessments(AuthenticatedUser actor);

    AssessmentStatusResponse submitAttempt(Long assessmentId, AuthenticatedUser actor);

    AssessmentStatusResponse grade(Long resultId, GradeAssessmentRequest request, AuthenticatedUser actor);
}
