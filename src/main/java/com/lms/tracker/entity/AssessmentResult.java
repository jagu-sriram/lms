package com.lms.tracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * A student's status for one assessment (attempts, score, pass/fail).
 */
@Entity
@Table(name = "assessment_results",
        uniqueConstraints = @UniqueConstraint(name = "uk_results_assessment_student", columnNames = {"assessment_id", "student_id"}),
        indexes = @Index(name = "idx_results_student", columnList = "student_id"))
public class AssessmentResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssessmentStatus status;

    @Column(name = "score")
    private Integer score;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(name = "feedback", length = 500)
    private String feedback;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AssessmentResult() {
        // for JPA
    }

    public AssessmentResult(Assessment assessment, User student) {
        this.assessment = assessment;
        this.student = student;
        this.status = AssessmentStatus.NOT_ATTEMPTED;
        this.attempts = 0;
        this.completed = false;
    }

    public boolean hasAttemptsRemaining() {
        return attempts < assessment.getMaxAttempts();
    }

    /** Student submits an attempt; it waits for grading. */
    public void recordAttempt(Instant now) {
        this.attempts++;
        this.status = AssessmentStatus.SUBMITTED;
        this.lastAttemptAt = now;
        this.score = null;
        this.feedback = null;
    }

    /** Instructor/admin grades the submitted attempt. */
    public void grade(int score, String feedback, User grader, Instant now) {
        this.score = score;
        this.feedback = feedback;
        this.gradedBy = grader;
        this.gradedAt = now;
        this.status = score >= assessment.getPassingScore() ? AssessmentStatus.PASSED : AssessmentStatus.FAILED;
        // Completed once passed, or once failed with no attempts left
        this.completed = status == AssessmentStatus.PASSED || !hasAttemptsRemaining();
    }

    public boolean belongsTo(Long userId) {
        return userId != null && student != null && userId.equals(student.getId());
    }

    public Assessment getAssessment() {
        return assessment;
    }

    public User getStudent() {
        return student;
    }

    public AssessmentStatus getStatus() {
        return status;
    }

    public Integer getScore() {
        return score;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getGradedAt() {
        return gradedAt;
    }

    public String getFeedback() {
        return feedback;
    }

    public Long getVersion() {
        return version;
    }
}
