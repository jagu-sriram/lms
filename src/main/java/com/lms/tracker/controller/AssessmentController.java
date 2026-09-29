package com.lms.tracker.controller;

import com.lms.tracker.dto.request.GradeAssessmentRequest;
import com.lms.tracker.dto.response.AssessmentStatusResponse;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.AssessmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    /** The authenticated student's assessment statuses across active enrollments. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public List<AssessmentStatusResponse> myAssessments(@AuthenticationPrincipal AuthenticatedUser actor) {
        return assessmentService.myAssessments(actor);
    }

    /** Student submits an attempt; it becomes SUBMITTED and waits for grading. */
    @PostMapping("/{assessmentId}/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public AssessmentStatusResponse submitAttempt(
            @PathVariable @Positive(message = "must be a positive number") Long assessmentId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return assessmentService.submitAttempt(assessmentId, actor);
    }

    /** The owning instructor (or an admin) grades a submitted attempt. */
    @PutMapping("/results/{resultId}/grade")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public AssessmentStatusResponse grade(
            @PathVariable @Positive(message = "must be a positive number") Long resultId,
            @Valid @RequestBody GradeAssessmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return assessmentService.grade(resultId, request, actor);
    }
}
