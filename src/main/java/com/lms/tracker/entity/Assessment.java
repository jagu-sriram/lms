package com.lms.tracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessments", indexes = @Index(name = "idx_assessments_course", columnList = "course_id"))
public class Assessment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    @Column(name = "passing_score", nullable = false)
    private int passingScore;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    protected Assessment() {
        // for JPA
    }

    public Assessment(Course course, String title, String description, int maxScore, int passingScore, int maxAttempts) {
        this.course = course;
        this.title = title;
        this.description = description;
        this.maxScore = maxScore;
        this.passingScore = passingScore;
        this.maxAttempts = maxAttempts;
    }

    public Course getCourse() {
        return course;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public int getPassingScore() {
        return passingScore;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
