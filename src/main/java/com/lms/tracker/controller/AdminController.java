package com.lms.tracker.controller;

import com.lms.tracker.dto.request.CreateUserRequest;
import com.lms.tracker.dto.request.UpdateUserStatusRequest;
import com.lms.tracker.dto.response.EnrollmentResponse;
import com.lms.tracker.dto.response.UserResponse;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.AdminService;
import com.lms.tracker.service.EnrollmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Everything under /api/admin/** additionally requires ROLE_ADMIN at the URL level (SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final EnrollmentService enrollmentService;

    public AdminController(AdminService adminService, EnrollmentService enrollmentService) {
        this.adminService = adminService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/users")
    public List<UserResponse> users(@AuthenticationPrincipal AuthenticatedUser actor) {
        return adminService.listUsers(actor);
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createUser(request, actor));
    }

    @PatchMapping("/users/{userId}/status")
    public UserResponse updateStatus(@PathVariable @Positive(message = "must be a positive number") Long userId,
                                     @Valid @RequestBody UpdateUserStatusRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return adminService.updateUserStatus(userId, request.enabled(), actor);
    }

    @GetMapping("/enrollments")
    public List<EnrollmentResponse> enrollments(@AuthenticationPrincipal AuthenticatedUser actor) {
        return enrollmentService.allEnrollments(actor);
    }
}
