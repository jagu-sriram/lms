package com.lms.tracker.service.impl;

import com.lms.tracker.entity.Assessment;
import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.Enrollment;
import com.lms.tracker.entity.EnrollmentStatus;
import com.lms.tracker.entity.Lesson;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.User;
import com.lms.tracker.repository.AssessmentRepository;
import com.lms.tracker.repository.AssessmentResultRepository;
import com.lms.tracker.repository.EnrollmentRepository;
import com.lms.tracker.repository.LessonRepository;
import com.lms.tracker.repository.ProgressRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps progress / assessment-result rows in sync with enrollments:
 * every (student, lesson) and (student, assessment) pair of an active enrollment has exactly one row.
 * All methods are idempotent, so re-enrolling after a drop keeps earlier progress.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ProgressProvisioner {

    private final LessonRepository lessonRepository;
    private final AssessmentRepository assessmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProgressRepository progressRepository;
    private final AssessmentResultRepository assessmentResultRepository;

    public ProgressProvisioner(LessonRepository lessonRepository,
                               AssessmentRepository assessmentRepository,
                               EnrollmentRepository enrollmentRepository,
                               ProgressRepository progressRepository,
                               AssessmentResultRepository assessmentResultRepository) {
        this.lessonRepository = lessonRepository;
        this.assessmentRepository = assessmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
        this.assessmentResultRepository = assessmentResultRepository;
    }

    public void provisionEnrollment(User student, Course course) {
        for (Lesson lesson : lessonRepository.findByCourseId(course.getId())) {
            createProgressIfMissing(student, lesson);
        }
        for (Assessment assessment : assessmentRepository.findByCourseId(course.getId())) {
            createResultIfMissing(student, assessment);
        }
    }

    public void provisionLesson(Lesson lesson) {
        for (Enrollment e : enrollmentRepository.findByCourseWithStudent(lesson.getCourse().getId(), EnrollmentStatus.ACTIVE)) {
            createProgressIfMissing(e.getStudent(), lesson);
        }
    }

    public void provisionAssessment(Assessment assessment) {
        for (Enrollment e : enrollmentRepository.findByCourseWithStudent(assessment.getCourse().getId(), EnrollmentStatus.ACTIVE)) {
            createResultIfMissing(e.getStudent(), assessment);
        }
    }

    private void createProgressIfMissing(User student, Lesson lesson) {
        if (!progressRepository.existsByStudentAndLesson(student.getId(), lesson.getId())) {
            progressRepository.save(new Progress(student, lesson));
        }
    }

    private void createResultIfMissing(User student, Assessment assessment) {
        if (!assessmentResultRepository.existsByStudentAndAssessment(student.getId(), assessment.getId())) {
            assessmentResultRepository.save(new AssessmentResult(assessment, student));
        }
    }
}
