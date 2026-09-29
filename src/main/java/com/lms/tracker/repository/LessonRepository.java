package com.lms.tracker.repository;

import com.lms.tracker.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    @Query("select l from Lesson l where l.course.id = :courseId order by l.displayOrder asc")
    List<Lesson> findByCourseId(@Param("courseId") Long courseId);

    @Query("select coalesce(max(l.displayOrder), 0) from Lesson l where l.course.id = :courseId")
    int findMaxDisplayOrder(@Param("courseId") Long courseId);

    /** Returns rows of [courseId, lessonCount] - one query instead of N. */
    @Query("select l.course.id, count(l) from Lesson l group by l.course.id")
    List<Object[]> countLessonsPerCourse();
}
