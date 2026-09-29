package com.lms.tracker.controller;

import com.lms.tracker.dto.request.CreateAssessmentRequest;
import com.lms.tracker.dto.request.CreateCourseRequest;
import com.lms.tracker.dto.request.CreateLessonRequest;
import com.lms.tracker.dto.response.AssessmentResponse;
import com.lms.tracker.dto.response.CourseDetailResponse;
import com.lms.tracker.dto.response.CourseSummaryResponse;
import com.lms.tracker.dto.response.EnrollmentResponse;
import com.lms.tracker.dto.response.LessonResponse;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.CourseService;
import com.lms.tracker.service.EnrollmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;

    public CourseController(CourseService courseService, EnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CourseSummaryResponse> listCourses(@AuthenticationPrincipal AuthenticatedUser actor) {
        return courseService.listCourses(actor);
    }

    @GetMapping("/{courseId}")
    @PreAuthorize("isAuthenticated()")
    public CourseDetailResponse getCourse(@PathVariable @Positive(message = "must be a positive number") Long courseId,
                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return courseService.getCourse(courseId, actor);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<CourseDetailResponse> createCourse(@Valid @RequestBody CreateCourseRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(request, actor));
    }

    @PostMapping("/{courseId}/lessons")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<LessonResponse> addLesson(@PathVariable @Positive(message = "must be a positive number") Long courseId,
                                                    @Valid @RequestBody CreateLessonRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.addLesson(courseId, request, actor));
    }

    @PostMapping("/{courseId}/assessments")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<AssessmentResponse> addAssessment(@PathVariable @Positive(message = "must be a positive number") Long courseId,
                                                            @Valid @RequestBody CreateAssessmentRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.addAssessment(courseId, request, actor));
    }

    /** The caller enrolls themselves. No student id is accepted. */
    @PostMapping("/{courseId}/enrollment")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<EnrollmentResponse> enroll(@PathVariable @Positive(message = "must be a positive number") Long courseId,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.enroll(courseId, actor));
    }

    @DeleteMapping("/{courseId}/enrollment")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> drop(@PathVariable @Positive(message = "must be a positive number") Long courseId,
                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        enrollmentService.drop(courseId, actor);
        return ResponseEntity.noContent().build();
    }
}
