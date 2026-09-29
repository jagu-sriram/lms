package com.lms.tracker.repository;

import com.lms.tracker.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    @Query("select a from Assessment a where a.course.id = :courseId order by a.id asc")
    List<Assessment> findByCourseId(@Param("courseId") Long courseId);

    @Query("select a from Assessment a join fetch a.course c join fetch c.instructor where a.id = :id")
    Optional<Assessment> findWithCourseById(@Param("id") Long id);

    /** Returns rows of [courseId, assessmentCount]. */
    @Query("select a.course.id, count(a) from Assessment a group by a.course.id")
    List<Object[]> countAssessmentsPerCourse();
}
