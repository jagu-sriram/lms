package com.lms.tracker.service;

import com.lms.tracker.dto.request.UpdateProgressRequest;
import com.lms.tracker.dto.response.CourseProgressReportResponse;
import com.lms.tracker.dto.response.ProgressRecordResponse;
import com.lms.tracker.dto.response.StudentProgressResponse;
import com.lms.tracker.security.AuthenticatedUser;

public interface ProgressService {

    /** GET /api/progress/me - always the caller's own data; takes no id at all. */
    StudentProgressResponse getMyProgress(AuthenticatedUser actor);

    /** GET /api/progress/course/{id} - scope depends on enrollment / ownership. */
    CourseProgressReportResponse getCourseProgress(Long courseId, AuthenticatedUser actor);

    /** PUT /api/progress/{id} - loads the record, then verifies ownership before changing it. */
    ProgressRecordResponse updateProgress(Long progressId, UpdateProgressRequest request, AuthenticatedUser actor);
}
