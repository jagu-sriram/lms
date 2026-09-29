package com.lms.tracker.dto.response;

import java.util.List;

public record InstructorDashboardResponse(
        String instructorName,
        int coursesTaught,
        int totalEnrolledStudents,
        int submissionsAwaitingGrading,
        List<InstructorCourseSummary> courses
) {
}
