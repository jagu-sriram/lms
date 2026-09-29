package com.lms.tracker.controller;

import com.lms.tracker.dto.response.AdminDashboardResponse;
import com.lms.tracker.dto.response.InstructorDashboardResponse;
import com.lms.tracker.dto.response.StudentDashboardResponse;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentDashboardResponse student(@AuthenticationPrincipal AuthenticatedUser actor) {
        return dashboardService.studentDashboard(actor);
    }

    @GetMapping("/instructor")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public InstructorDashboardResponse instructor(@AuthenticationPrincipal AuthenticatedUser actor) {
        return dashboardService.instructorDashboard(actor);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardResponse admin(@AuthenticationPrincipal AuthenticatedUser actor) {
        return dashboardService.adminDashboard(actor);
    }
}
