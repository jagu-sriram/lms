package com.lms.tracker.repository;

import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.ProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProgressRepository extends JpaRepository<Progress, Long> {

    /** Loads the record together with everything needed for the ownership check. */
    @Query("select p from Progress p join fetch p.student join fetch p.lesson "
            + "join fetch p.course c join fetch c.instructor where p.id = :id")
    Optional<Progress> findDetailedById(@Param("id") Long id);

    @Query("select p from Progress p join fetch p.lesson l "
            + "where p.student.id = :studentId order by p.course.id asc, l.displayOrder asc")
    List<Progress> findByStudentWithLesson(@Param("studentId") Long studentId);

    @Query("select p from Progress p join fetch p.lesson l "
            + "where p.student.id = :studentId and p.course.id = :courseId order by l.displayOrder asc")
    List<Progress> findByStudentAndCourse(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    @Query("select p from Progress p join fetch p.lesson l "
            + "where p.course.id in :courseIds order by l.displayOrder asc")
    List<Progress> findByCourses(@Param("courseIds") Collection<Long> courseIds);

    @Query("select case when count(p) > 0 then true else false end from Progress p "
            + "where p.student.id = :studentId and p.lesson.id = :lessonId")
    boolean existsByStudentAndLesson(@Param("studentId") Long studentId, @Param("lessonId") Long lessonId);

    long countByStatus(ProgressStatus status);
}
