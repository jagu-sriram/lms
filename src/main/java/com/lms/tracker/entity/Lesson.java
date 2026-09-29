package com.lms.tracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "lessons",
        uniqueConstraints = @UniqueConstraint(name = "uk_lessons_course_order", columnNames = {"course_id", "display_order"}))
public class Lesson extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "content", length = 5000)
    private String content;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    protected Lesson() {
        // for JPA
    }

    public Lesson(Course course, String title, String content, int displayOrder, int durationMinutes) {
        this.course = course;
        this.title = title;
        this.content = content;
        this.displayOrder = displayOrder;
        this.durationMinutes = durationMinutes;
    }

    public Course getCourse() {
        return course;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }
}
