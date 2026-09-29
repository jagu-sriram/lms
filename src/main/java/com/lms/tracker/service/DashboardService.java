package com.lms.tracker.service;

import com.lms.tracker.dto.response.AdminDashboardResponse;
import com.lms.tracker.dto.response.InstructorDashboardResponse;
import com.lms.tracker.dto.response.StudentDashboardResponse;
import com.lms.tracker.security.AuthenticatedUser;

public interface DashboardService {

    StudentDashboardResponse studentDashboard(AuthenticatedUser actor);

    InstructorDashboardResponse instructorDashboard(AuthenticatedUser actor);

    AdminDashboardResponse adminDashboard(AuthenticatedUser actor);
}
