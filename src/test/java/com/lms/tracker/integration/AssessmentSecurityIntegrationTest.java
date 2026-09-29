package com.lms.tracker.integration;

import com.lms.tracker.entity.Assessment;
import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.AssessmentStatus;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssessmentSecurityIntegrationTest extends IntegrationTestSupport {

    private User instructorA;
    private User instructorB;
    private User studentA;
    private User studentB;
    private User outsider;
    private Assessment quiz;

    @BeforeEach
    void setUpFixture() {
        instructorA = createUser("Alice Instructor", "alice@test.local", RoleName.INSTRUCTOR);
        instructorB = createUser("Bob Instructor", "bob@test.local", RoleName.INSTRUCTOR);
        studentA = createUser("Student Alpha", "student.a@test.local", RoleName.STUDENT);
        studentB = createUser("Student Beta", "student.b@test.local", RoleName.STUDENT);
        outsider = createUser("Student Outsider", "outsider@test.local", RoleName.STUDENT);

        Course course = createCourse(instructorA, "Course A");
        addLesson(course, "Lesson 1", 1);
        quiz = addAssessment(course, "Quiz 1", 100, 60, 2);
        enroll(studentA, course);
        enroll(studentB, course);
    }

    private AssessmentResult resultOf(User student) {
        return assessmentResultRepository.findByAssessmentAndStudent(quiz.getId(), student.getId()).orElseThrow();
    }

    private String gradeBody(int score) throws Exception {
        return json(Map.of("score", score));
    }

    @Test
    @DisplayName("enrolled student submits an attempt; a second submission while pending gives 409")
    void submitAttempt() throws Exception {
        String token = bearer(studentA);
        mockMvc.perform(post("/api/assessments/{id}/attempts", quiz.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUBMITTED")))
                .andExpect(jsonPath("$.attempts", is(1)));
        mockMvc.perform(post("/api/assessments/{id}/attempts", quiz.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict());

        // student B's result is untouched
        assertThat(resultOf(studentB).getStatus()).isEqualTo(AssessmentStatus.NOT_ATTEMPTED);
    }

    @Test
    @DisplayName("student who is not enrolled cannot submit (403); unknown assessment gives 404")
    void outsiderCannotSubmit() throws Exception {
        mockMvc.perform(post("/api/assessments/{id}/attempts", quiz.getId()).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/assessments/{id}/attempts", 999_999L).header(HttpHeaders.AUTHORIZATION, bearer(studentA)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("students can never grade - not even their own result (403)")
    void studentCannotGrade() throws Exception {
        mockMvc.perform(post("/api/assessments/{id}/attempts", quiz.getId()).header(HttpHeaders.AUTHORIZATION, bearer(studentA)))
                .andExpect(status().isOk());
        Long resultId = resultOf(studentA).getId();

        mockMvc.perform(put("/api/assessments/results/{id}/grade", resultId).header(HttpHeaders.AUTHORIZATION, bearer(studentA))
                        .contentType(MediaType.APPLICATION_JSON).content(gradeBody(100)))
                .andExpect(status().isForbidden());
        assertThat(resultOf(studentA).getScore()).isNull();
    }

    @Test
    @DisplayName("instructor of another course cannot grade (403); owning instructor can (200)")
    void gradingOwnership() throws Exception {
        mockMvc.perform(post("/api/assessments/{id}/attempts", quiz.getId()).header(HttpHeaders.AUTHORIZATION, bearer(studentA)))
                .andExpect(status().isOk());
        Long resultId = resultOf(studentA).getId();

        mockMvc.perform(put("/api/assessments/results/{id}/grade", resultId).header(HttpHeaders.AUTHORIZATION, bearer(instructorB))
                        .contentType(MediaType.APPLICATION_JSON).content(gradeBody(90)))
                .andExpect(status().isForbidden());
        assertThat(resultOf(studentA).getStatus()).isEqualTo(AssessmentStatus.SUBMITTED);

        // score above the maximum is rejected
        mockMvc.perform(put("/api/assessments/results/{id}/grade", resultId).header(HttpHeaders.AUTHORIZATION, bearer(instructorA))
                        .contentType(MediaType.APPLICATION_JSON).content(gradeBody(150)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/assessments/results/{id}/grade", resultId).header(HttpHeaders.AUTHORIZATION, bearer(instructorA))
                        .contentType(MediaType.APPLICATION_JSON).content(gradeBody(75)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PASSED")))
                .andExpect(jsonPath("$.score", is(75)))
                .andExpect(jsonPath("$.completed", is(true)));

        // grading again without a new submission is a conflict
        mockMvc.perform(put("/api/assessments/results/{id}/grade", resultId).header(HttpHeaders.AUTHORIZATION, bearer(instructorA))
                        .contentType(MediaType.APPLICATION_JSON).content(gradeBody(10)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("failing uses an attempt; after the last failed attempt no more submissions are allowed")
    void attemptsAreLimited() throws Exception {
        String student = bearer(studentA);
        String instructor = bearer(instructorA);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/assessments/{id}/attempts", quiz.getId()).header(HttpHeaders.AUTHORIZATION, student))
                    .andExpect(status().isOk());
            mockMvc.perform(put("/api/assessments/results/{id}/grade", resultOf(studentA).getId()).header(HttpHeaders.AUTHORIZATION, instructor)
                            .contentType(MediaType.APPLICATION_JSON).content(gradeBody(20)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("FAILED")));
        }
        mockMvc.perform(post("/api/assessments/{id}/attempts", quiz.getId()).header(HttpHeaders.AUTHORIZATION, student))
                .andExpect(status().isConflict());
        assertThat(resultOf(studentA).isCompleted()).isTrue();
    }

    @Test
    @DisplayName("GET /api/assessments/me returns only the caller's own results")
    void myAssessmentsOnlyOwn() throws Exception {
        mockMvc.perform(get("/api/assessments/me").header(HttpHeaders.AUTHORIZATION, bearer(studentA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].resultId", is(resultOf(studentA).getId().intValue())));
        mockMvc.perform(get("/api/assessments/me").header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
