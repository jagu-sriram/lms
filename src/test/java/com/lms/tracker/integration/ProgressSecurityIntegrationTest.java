package com.lms.tracker.integration;

import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.ProgressStatus;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.support.IntegrationTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for the three progress APIs, including IDOR / BOLA attack scenarios.
 *
 * Fixture:
 *   instructorA teaches courseA (3 lessons, 1 assessment) - studentA and studentB are enrolled
 *   instructorB teaches courseB (2 lessons)                - only studentB is enrolled
 *   studentC is not enrolled anywhere; admin is an administrator
 */
class ProgressSecurityIntegrationTest extends IntegrationTestSupport {

    private User instructorA;
    private User instructorB;
    private User studentA;
    private User studentB;
    private User studentC;
    private User admin;
    private Course courseA;
    private Course courseB;

    @BeforeEach
    void setUpFixture() {
        instructorA = createUser("Alice Instructor", "alice@test.local", RoleName.INSTRUCTOR);
        instructorB = createUser("Bob Instructor", "bob@test.local", RoleName.INSTRUCTOR);
        studentA = createUser("Student Alpha", "student.a@test.local", RoleName.STUDENT);
        studentB = createUser("Student Beta", "student.b@test.local", RoleName.STUDENT);
        studentC = createUser("Student Gamma", "student.c@test.local", RoleName.STUDENT);
        admin = createUser("Admin User", "admin@test.local", RoleName.ADMIN);

        courseA = createCourse(instructorA, "Course A");
        addLesson(courseA, "A - Lesson 1", 1);
        addLesson(courseA, "A - Lesson 2", 2);
        addLesson(courseA, "A - Lesson 3", 3);
        addAssessment(courseA, "A - Quiz", 100, 60, 2);

        courseB = createCourse(instructorB, "Course B");
        addLesson(courseB, "B - Lesson 1", 1);
        addLesson(courseB, "B - Lesson 2", 2);

        enroll(studentA, courseA);
        enroll(studentB, courseA);
        enroll(studentB, courseB);
    }

    private String progressBody(int percentage) throws Exception {
        return json(Map.of("completionPercentage", percentage));
    }

    // ================================================================== GET /api/progress/me

    @Nested
    @DisplayName("GET /api/progress/me")
    class MyProgress {

        @Test
        @DisplayName("student gets 200 and only their own progress")
        void studentGetsOwnProgressOnly() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/progress/me").header(HttpHeaders.AUTHORIZATION, bearer(studentA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.studentId", is(studentA.getId().intValue())))
                    .andExpect(jsonPath("$.enrolledCourses", is(1)))
                    .andExpect(jsonPath("$.courses", hasSize(1)))
                    .andExpect(jsonPath("$.courses[0].courseId", is(courseA.getId().intValue())))
                    .andExpect(jsonPath("$.courses[0].lessons", hasSize(3)))
                    .andExpect(jsonPath("$.courses[0].assessments", hasSize(1)))
                    .andReturn();

            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(body).doesNotContain("student.b@test.local", "Student Beta", "Course B");

            // every progress id returned belongs to student A
            List<Long> ownIds = progressOf(studentA, courseA).stream().map(Progress::getId).toList();
            for (Progress p : progressOf(studentB, courseA)) {
                assertThat(ownIds).doesNotContain(p.getId());
            }
        }

        @Test
        @DisplayName("there is no way to ask for another student's data: query parameters are ignored")
        void queryParametersCannotSelectAnotherStudent() throws Exception {
            mockMvc.perform(get("/api/progress/me")
                            .param("studentId", studentB.getId().toString())
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.studentId", is(studentA.getId().intValue())));
        }

        @Test
        @DisplayName("instructor and admin get 403 (endpoint is for students)")
        void nonStudentsForbidden() throws Exception {
            mockMvc.perform(get("/api/progress/me").header(HttpHeaders.AUTHORIZATION, bearer(instructorA)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/progress/me").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request gets 401 with the JSON error body")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/progress/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.error", is("Unauthorized")))
                    .andExpect(jsonPath("$.message", notNullValue()))
                    .andExpect(jsonPath("$.path", is("/api/progress/me")))
                    .andExpect(jsonPath("$.timestamp", notNullValue()));
        }
    }

    // ================================================================== GET /api/progress/course/{id}

    @Nested
    @DisplayName("GET /api/progress/course/{id}")
    class CourseProgress {

        @Test
        @DisplayName("enrolled student sees ONLY their own row even though other students are enrolled")
        void enrolledStudentSeesOnlySelf() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/progress/course/{id}", courseA.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope", is("SELF")))
                    .andExpect(jsonPath("$.students", hasSize(1)))
                    .andExpect(jsonPath("$.students[0].studentId", is(studentA.getId().intValue())))
                    .andReturn();
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .doesNotContain("student.b@test.local");
        }

        @Test
        @DisplayName("student who is not enrolled gets 403")
        void notEnrolledStudentForbidden() throws Exception {
            mockMvc.perform(get("/api/progress/course/{id}", courseB.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentA)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status", is(403)))
                    .andExpect(jsonPath("$.error", is("Forbidden")))
                    .andExpect(jsonPath("$.message", is("You are not enrolled in this course")))
                    .andExpect(jsonPath("$.path", is("/api/progress/course/" + courseB.getId())));

            mockMvc.perform(get("/api/progress/course/{id}", courseA.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentC)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("student who dropped the course loses access (403)")
        void droppedStudentForbidden() throws Exception {
            drop(studentA, courseA);
            mockMvc.perform(get("/api/progress/course/{id}", courseA.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentA)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("instructor sees all enrolled students of a course they teach")
        void instructorSeesOwnCourse() throws Exception {
            mockMvc.perform(get("/api/progress/course/{id}", courseA.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(instructorA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope", is("ALL_STUDENTS")))
                    .andExpect(jsonPath("$.enrolledStudents", is(2)))
                    .andExpect(jsonPath("$.students", hasSize(2)))
                    .andExpect(jsonPath("$.students[0].lessons", hasSize(3)))
                    .andExpect(jsonPath("$.students[0].assessments", hasSize(1)));
        }

        @Test
        @DisplayName("instructor A gets 403 for instructor B's course")
        void instructorForbiddenForOtherInstructorsCourse() throws Exception {
            mockMvc.perform(get("/api/progress/course/{id}", courseB.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(instructorA)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message", is("You are not authorized to view progress for this course")));
        }

        @Test
        @DisplayName("admin can view any course")
        void adminSeesAnyCourse() throws Exception {
            mockMvc.perform(get("/api/progress/course/{id}", courseB.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope", is("ALL_STUDENTS")))
                    .andExpect(jsonPath("$.students", hasSize(1)))
                    .andExpect(jsonPath("$.students[0].studentId", is(studentB.getId().intValue())));
        }

        @Test
        @DisplayName("unknown course gives 404, invalid ids give 400, no token gives 401")
        void invalidIds() throws Exception {
            mockMvc.perform(get("/api/progress/course/{id}", 999_999L).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)));
            mockMvc.perform(get("/api/progress/course/0").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/progress/course/-3").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/progress/course/abc").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.courseId", notNullValue()));
            mockMvc.perform(get("/api/progress/course/{id}", courseA.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ================================================================== PUT /api/progress/{id}

    @Nested
    @DisplayName("PUT /api/progress/{id}")
    class UpdateProgress {

        @Test
        @DisplayName("student updates their own progress: 200 and persisted")
        void studentUpdatesOwnProgress() throws Exception {
            Progress own = progressOf(studentA, courseA).get(0);

            mockMvc.perform(put("/api/progress/{id}", own.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(progressBody(60)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(own.getId().intValue())))
                    .andExpect(jsonPath("$.studentId", is(studentA.getId().intValue())))
                    .andExpect(jsonPath("$.completionPercentage", is(60)))
                    .andExpect(jsonPath("$.status", is("IN_PROGRESS")))
                    .andExpect(jsonPath("$.lastAccessedAt", notNullValue()));

            Progress saved = reloadProgress(own.getId());
            assertThat(saved.getCompletionPercentage()).isEqualTo(60);
            assertThat(saved.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("completing a lesson sets COMPLETED and completedAt")
        void completeLesson() throws Exception {
            Progress own = progressOf(studentA, courseA).get(1);
            mockMvc.perform(put("/api/progress/{id}", own.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("completionPercentage", 100, "status", "COMPLETED"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COMPLETED")))
                    .andExpect(jsonPath("$.completedAt", notNullValue()));
        }

        @Test
        @DisplayName("IDOR: student A updating student B's progress gets 403 and nothing changes")
        void studentCannotUpdateAnotherStudentsProgress() throws Exception {
            Progress victim = progressOf(studentB, courseA).get(0);

            mockMvc.perform(put("/api/progress/{id}", victim.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(progressBody(100)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status", is(403)))
                    .andExpect(jsonPath("$.error", is("Forbidden")))
                    .andExpect(jsonPath("$.message", is("You are not authorized to modify this progress record")))
                    .andExpect(jsonPath("$.path", is("/api/progress/" + victim.getId())));

            Progress unchanged = reloadProgress(victim.getId());
            assertThat(unchanged.getCompletionPercentage()).isZero();
            assertThat(unchanged.getStatus()).isEqualTo(ProgressStatus.NOT_STARTED);
            assertThat(unchanged.getLastAccessedAt()).isNull();
        }

        @Test
        @DisplayName("BOLA sweep: student A tries every one of student B's progress ids - all 403, none modified")
        void idEnumerationAttack() throws Exception {
            List<Progress> victims = new java.util.ArrayList<>(progressOf(studentB, courseA));
            victims.addAll(progressOf(studentB, courseB));
            assertThat(victims).hasSize(5);

            for (Progress victim : victims) {
                mockMvc.perform(put("/api/progress/{id}", victim.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearer(studentA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(progressBody(100)))
                        .andExpect(status().isForbidden());
            }
            for (Progress victim : victims) {
                assertThat(reloadProgress(victim.getId()).getCompletionPercentage()).isZero();
            }
        }

        @Test
        @DisplayName("mass assignment: extra fields like studentId/lessonId in the body are rejected with 400")
        void massAssignmentRejected() throws Exception {
            Progress own = progressOf(studentA, courseA).get(0);

            mockMvc.perform(put("/api/progress/{id}", own.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("completionPercentage", 50, "studentId", studentB.getId()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.studentId", notNullValue()));

            assertThat(reloadProgress(own.getId()).getCompletionPercentage()).isZero();
            // ownership did not move: the record is still in student A's list and not in B's
            assertThat(progressOf(studentA, courseA)).extracting(Progress::getId).contains(own.getId());
            assertThat(progressOf(studentB, courseA)).extracting(Progress::getId).doesNotContain(own.getId());
        }

        @Test
        @DisplayName("student who dropped the course cannot update their old progress (403)")
        void droppedStudentCannotUpdate() throws Exception {
            Progress own = progressOf(studentA, courseA).get(0);
            drop(studentA, courseA);

            mockMvc.perform(put("/api/progress/{id}", own.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(progressBody(40)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message", is("You are not actively enrolled in this course")));
        }

        @Test
        @DisplayName("instructor cannot modify student progress, even in their own course (403)")
        void instructorCannotModifyProgress() throws Exception {
            Progress record = progressOf(studentA, courseA).get(0);

            mockMvc.perform(put("/api/progress/{id}", record.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(instructorA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(progressBody(100)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/progress/{id}", progressOf(studentB, courseB).get(0).getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(instructorA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(progressBody(100)))
                    .andExpect(status().isForbidden());

            assertThat(reloadProgress(record.getId()).getCompletionPercentage()).isZero();
        }

        @Test
        @DisplayName("admin can correct any progress record (200)")
        void adminCanUpdate() throws Exception {
            Progress record = progressOf(studentB, courseB).get(1);
            mockMvc.perform(put("/api/progress/{id}", record.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(progressBody(100)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COMPLETED")));
        }

        @Test
        @DisplayName("unauthenticated update gets 401 and nothing changes")
        void unauthenticatedUpdate() throws Exception {
            Progress record = progressOf(studentA, courseA).get(0);
            mockMvc.perform(put("/api/progress/{id}", record.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(progressBody(100)))
                    .andExpect(status().isUnauthorized());
            assertThat(reloadProgress(record.getId()).getCompletionPercentage()).isZero();
        }

        @Test
        @DisplayName("invalid progress ids: unknown -> 404, zero/negative/non-numeric -> 400")
        void invalidProgressIds() throws Exception {
            mockMvc.perform(put("/api/progress/{id}", 999_999L)
                            .header(HttpHeaders.AUTHORIZATION, bearer(studentA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(progressBody(10)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", is("Progress record with id 999999 was not found")));
            for (String badId : List.of("0", "-1", "abc", "1.5", "99999999999999999999")) {
                mockMvc.perform(put("/api/progress/" + badId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(studentA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(progressBody(10)))
                        .andExpect(status().isBadRequest());
            }
        }

        @Test
        @DisplayName("invalid input is rejected with 400 and field errors")
        void invalidInput() throws Exception {
            Long id = progressOf(studentA, courseA).get(0).getId();
            String token = bearer(studentA);

            record Case(String body, String field) { }
            List<Case> cases = List.of(
                    new Case("{\"completionPercentage\":150}", "completionPercentage"),
                    new Case("{\"completionPercentage\":-1}", "completionPercentage"),
                    new Case("{}", "completionPercentage"),
                    new Case("{\"completionPercentage\":null}", "completionPercentage"),
                    new Case("{\"completionPercentage\":50.5}", "completionPercentage"),
                    new Case("{\"completionPercentage\":\"lots\"}", "completionPercentage"),
                    new Case("{\"completionPercentage\":50,\"status\":\"DONE\"}", "status"));
            for (Case c : cases) {
                mockMvc.perform(put("/api/progress/{id}", id)
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(c.body()))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status", is(400)))
                        .andExpect(jsonPath("$.fieldErrors." + c.field(), notNullValue()));
            }

            // status inconsistent with percentage
            mockMvc.perform(put("/api/progress/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"completionPercentage\":50,\"status\":\"COMPLETED\"}"))
                    .andExpect(status().isBadRequest());
            // malformed JSON
            mockMvc.perform(put("/api/progress/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"completionPercentage\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("Malformed JSON request body")));
            // missing body
            mockMvc.perform(put("/api/progress/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            assertThat(reloadProgress(id).getCompletionPercentage()).isZero();
        }
    }

    // ================================================================== token attacks

    @Nested
    @DisplayName("Forged, tampered and expired tokens")
    class TokenAttacks {

        @Test
        @DisplayName("tampered token (payload changed) gets 401")
        void tamperedToken() throws Exception {
            String token = bearer(studentA).substring("Bearer ".length());
            String[] parts = token.split("\\.");
            String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ("{\"sub\":\"" + studentB.getId() + "\",\"jti\":\"x\",\"iss\":\"lms-progress-tracker\",\"exp\":4102444800}")
                            .getBytes(StandardCharsets.UTF_8));
            String forged = parts[0] + "." + forgedPayload + "." + parts[2];

            mockMvc.perform(get("/api/progress/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", is("Invalid or expired token")));
        }

        @Test
        @DisplayName("token signed with a different key gets 401")
        void tokenSignedWithOtherKey() throws Exception {
            String forged = Jwts.builder()
                    .subject(studentB.getId().toString())
                    .id("attacker")
                    .issuer("lms-progress-tracker")
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(Keys.hmacShaKeyFor("attacker-controlled-secret-key-0123456789!!".getBytes(StandardCharsets.UTF_8)))
                    .compact();
            mockMvc.perform(get("/api/progress/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("unsigned 'alg: none' token gets 401")
        void unsignedToken() throws Exception {
            String header = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
            String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ("{\"sub\":\"" + studentA.getId() + "\",\"jti\":\"x\",\"iss\":\"lms-progress-tracker\",\"exp\":4102444800}")
                            .getBytes(StandardCharsets.UTF_8));
            mockMvc.perform(get("/api/progress/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + header + "." + payload + "."))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("garbage bearer token gets 401")
        void garbageToken() throws Exception {
            mockMvc.perform(get("/api/progress/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
