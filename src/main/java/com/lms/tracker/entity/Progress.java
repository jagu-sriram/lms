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
 * One row per (student, lesson). Rows are provisioned when a student enrolls,
 * or when a lesson is added to a course that already has enrolled students.
 */
@Entity
@Table(name = "progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_progress_student_lesson", columnNames = {"student_id", "lesson_id"}),
        indexes = {
                @Index(name = "idx_progress_student_course", columnList = "student_id, course_id"),
                @Index(name = "idx_progress_course", columnList = "course_id")
        })
public class Progress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProgressStatus status;

    @Column(name = "completion_percentage", nullable = false)
    private int completionPercentage;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Optimistic locking - concurrent updates to the same record get HTTP 409 instead of a lost update. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Progress() {
        // for JPA
    }

    public Progress(User student, Lesson lesson) {
        this.student = student;
        this.lesson = lesson;
        this.course = lesson.getCourse();
        this.status = ProgressStatus.NOT_STARTED;
        this.completionPercentage = 0;
    }

    /**
     * Applies an already validated update.
     */
    public void applyUpdate(int percentage, ProgressStatus newStatus, Instant now) {
        this.completionPercentage = percentage;
        this.status = newStatus;
        this.lastAccessedAt = now;
        if (newStatus == ProgressStatus.COMPLETED) {
            if (this.completedAt == null) {
                this.completedAt = now;
            }
        } else {
            this.completedAt = null;
        }
    }

    public boolean belongsTo(Long userId) {
        return userId != null && student != null && userId.equals(student.getId());
    }

    public User getStudent() {
        return student;
    }

    public Course getCourse() {
        return course;
    }

    public Lesson getLesson() {
        return lesson;
    }

    public ProgressStatus getStatus() {
        return status;
    }

    public int getCompletionPercentage() {
        return completionPercentage;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getVersion() {
        return version;
    }
}
