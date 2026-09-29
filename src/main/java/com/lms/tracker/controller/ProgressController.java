package com.lms.tracker.controller;

import com.lms.tracker.dto.request.UpdateProgressRequest;
import com.lms.tracker.dto.response.CourseProgressReportResponse;
import com.lms.tracker.dto.response.ProgressRecordResponse;
import com.lms.tracker.dto.response.StudentProgressResponse;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.ProgressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Layer 1 (here): coarse role checks with @PreAuthorize.
 * Layer 2 (ProgressService + AccessPolicy): object-level ownership checks after loading the data.
 */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    /** The authenticated student's own progress. There is intentionally no id parameter. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentProgressResponse myProgress(@AuthenticationPrincipal AuthenticatedUser actor) {
        return progressService.getMyProgress(actor);
    }

    /** Student: own progress if enrolled. Instructor: all students if they teach it. Admin: all students. */
    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'ADMIN')")
    public CourseProgressReportResponse courseProgress(
            @PathVariable @Positive(message = "must be a positive number") Long courseId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return progressService.getCourseProgress(courseId, actor);
    }

    /** Student: only their own record while enrolled. Admin: corrections. Instructor: never (read-only). */
    @PutMapping("/{progressId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ProgressRecordResponse updateProgress(
            @PathVariable @Positive(message = "must be a positive number") Long progressId,
            @Valid @RequestBody UpdateProgressRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return progressService.updateProgress(progressId, request, actor);
    }
}
