package com.lms.tracker.service;

import com.lms.tracker.dto.request.CreateAssessmentRequest;
import com.lms.tracker.dto.request.CreateCourseRequest;
import com.lms.tracker.dto.request.CreateLessonRequest;
import com.lms.tracker.dto.response.AssessmentResponse;
import com.lms.tracker.dto.response.CourseDetailResponse;
import com.lms.tracker.dto.response.CourseSummaryResponse;
import com.lms.tracker.dto.response.LessonResponse;
import com.lms.tracker.security.AuthenticatedUser;

import java.util.List;

public interface CourseService {

    List<CourseSummaryResponse> listCourses(AuthenticatedUser actor);

    CourseDetailResponse getCourse(Long courseId, AuthenticatedUser actor);

    CourseDetailResponse createCourse(CreateCourseRequest request, AuthenticatedUser actor);

    LessonResponse addLesson(Long courseId, CreateLessonRequest request, AuthenticatedUser actor);

    AssessmentResponse addAssessment(Long courseId, CreateAssessmentRequest request, AuthenticatedUser actor);
}
