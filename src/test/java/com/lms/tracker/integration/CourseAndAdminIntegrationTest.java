package com.lms.tracker.integration;

import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseAndAdminIntegrationTest extends IntegrationTestSupport {

    private User instructorA;
    private User instructorB;
    private User student;
    private User admin;
    private Course courseA;

    @BeforeEach
    void setUpFixture() {
        instructorA = createUser("Alice Instructor", "alice@test.local", RoleName.INSTRUCTOR);
        instructorB = createUser("Bob Instructor", "bob@test.local", RoleName.INSTRUCTOR);
        student = createUser("Some Student", "student@test.local", RoleName.STUDENT);
        admin = createUser("Admin User", "admin@test.local", RoleName.ADMIN);
        courseA = createCourse(instructorA, "Course A");
        addLesson(courseA, "Lesson 1", 1);
    }

    // ------------------------------------------------------------------ course creation

    @Test
    @DisplayName("student cannot create a course (403)")
    void studentCannotCreateCourse() throws Exception {
        mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Hacked course"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("instructor creates a course they own (201)")
    void instructorCreatesCourse() throws Exception {
        mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(instructorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Docker Basics", "description", "Containers 101"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instructorId", is(instructorA.getId().intValue())))
                .andExpect(jsonPath("$.canManage", is(true)));
    }

    @Test
    @DisplayName("instructor cannot create a course owned by another instructor (403)")
    void instructorCannotCreateCourseForOthers() throws Exception {
        mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(instructorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Not mine", "instructorId", instructorB.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin must name an existing INSTRUCTOR as owner")
    void adminCreatesCourseForInstructor() throws Exception {
        String token = bearer(admin);
        mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "No owner"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Student owner", "instructorId", student.getId()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Owned by Bob", "instructorId", instructorB.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instructorId", is(instructorB.getId().intValue())));
    }

    @Test
    @DisplayName("course validation errors give 400")
    void invalidCourse() throws Exception {
        mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(instructorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "ab"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title", notNullValue()));
    }

    // ------------------------------------------------------------------ lessons / assessments

    @Test
    @DisplayName("only the owning instructor (or admin) can add lessons; new lessons get progress rows for enrolled students")
    void addLessonOwnership() throws Exception {
        enroll(student, courseA);
        String lesson = json(Map.of("title", "Lesson 2", "durationMinutes", 20));

        mockMvc.perform(post("/api/courses/{id}/lessons", courseA.getId()).header(HttpHeaders.AUTHORIZATION, bearer(instructorB))
                        .contentType(MediaType.APPLICATION_JSON).content(lesson))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/courses/{id}/lessons", courseA.getId()).header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON).content(lesson))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/courses/{id}/lessons", courseA.getId()).header(HttpHeaders.AUTHORIZATION, bearer(instructorA))
                        .contentType(MediaType.APPLICATION_JSON).content(lesson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayOrder", is(2)));

        assertThat(progressOf(student, courseA)).hasSize(2);
    }

    @Test
    @DisplayName("assessment with passingScore > maxScore gives 400")
    void invalidAssessment() throws Exception {
        mockMvc.perform(post("/api/courses/{id}/assessments", courseA.getId()).header(HttpHeaders.AUTHORIZATION, bearer(instructorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Quiz", "maxScore", 50, "passingScore", 80, "maxAttempts", 1))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ enrollment

    @Test
    @DisplayName("student enrolls (201), duplicate gives 409, drop gives 204, unknown course 404")
    void enrollmentFlow() throws Exception {
        String token = bearer(student);
        mockMvc.perform(post("/api/courses/{id}/enrollment", courseA.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId", is(student.getId().intValue())))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
        assertThat(progressOf(student, courseA)).hasSize(1);

        mockMvc.perform(post("/api/courses/{id}/enrollment", courseA.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/enrollments/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(delete("/api/courses/{id}/enrollment", courseA.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/courses/{id}/enrollment", 999_999L).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("instructors and admins cannot enroll (403)")
    void nonStudentsCannotEnroll() throws Exception {
        mockMvc.perform(post("/api/courses/{id}/enrollment", courseA.getId()).header(HttpHeaders.AUTHORIZATION, bearer(instructorB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/courses/{id}/enrollment", courseA.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lesson content is hidden until the student enrolls")
    void lessonContentVisibility() throws Exception {
        String token = bearer(student);
        mockMvc.perform(get("/api/courses/{id}", courseA.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled", is(false)))
                .andExpect(jsonPath("$.canManage", is(false)))
                .andExpect(jsonPath("$.lessons[0].content", nullValue()));
        enroll(student, courseA);
        mockMvc.perform(get("/api/courses/{id}", courseA.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.enrolled", is(true)))
                .andExpect(jsonPath("$.lessons[0].content", notNullValue()));
    }

    @Test
    @DisplayName("course list is available to every authenticated user, not to anonymous")
    void listCourses() throws Exception {
        mockMvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lessonCount", is(1)))
                .andExpect(jsonPath("$[0].enrolled", is(false)));
        mockMvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ dashboards

    @Test
    @DisplayName("each dashboard is only available to its role")
    void dashboards() throws Exception {
        enroll(student, courseA);
        mockMvc.perform(get("/api/dashboard/student").header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolledCourses", is(1)));
        mockMvc.perform(get("/api/dashboard/instructor").header(HttpHeaders.AUTHORIZATION, bearer(instructorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesTaught", is(1)))
                .andExpect(jsonPath("$.totalEnrolledStudents", is(1)));
        mockMvc.perform(get("/api/dashboard/instructor").header(HttpHeaders.AUTHORIZATION, bearer(instructorB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesTaught", is(0)));
        mockMvc.perform(get("/api/dashboard/admin").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", is(4)))
                .andExpect(jsonPath("$.activeEnrollments", is(1)));

        mockMvc.perform(get("/api/dashboard/admin").header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/admin").header(HttpHeaders.AUTHORIZATION, bearer(instructorA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/student").header(HttpHeaders.AUTHORIZATION, bearer(instructorA)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ admin API

    @Test
    @DisplayName("admin API: 403 for students/instructors, 200 for admins, never exposes password hashes")
    void adminAccess() throws Exception {
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
        mockMvc.perform(get("/api/admin/enrollments").header(HttpHeaders.AUTHORIZATION, bearer(instructorA)))
                .andExpect(status().isForbidden());

        MvcResult result = mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password", "$2a$", "$2b$");

        mockMvc.perform(get("/api/admin/enrollments").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("admin creates an instructor; the new account can log in")
    void adminCreatesInstructor() throws Exception {
        mockMvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "New Instructor", "email", "new.instructor@test.local",
                                "password", "Teach!ng123", "role", "INSTRUCTOR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("INSTRUCTOR")));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "new.instructor@test.local", "password", "Teach!ng123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role", is("INSTRUCTOR")));
    }

    @Test
    @DisplayName("admin can disable a user (their token stops working) but cannot disable themselves")
    void adminDisablesUser() throws Exception {
        String studentToken = bearer(student);
        String adminToken = bearer(admin);

        mockMvc.perform(patch("/api/admin/users/{id}/status", admin.getId()).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/users/{id}/status", student.getId()).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));
        mockMvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isUnauthorized());
    }
}
