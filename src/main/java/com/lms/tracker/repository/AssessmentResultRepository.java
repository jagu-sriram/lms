package com.lms.tracker.repository;

import com.lms.tracker.entity.AssessmentResult;
import com.lms.tracker.entity.AssessmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {

    @Query("select r from AssessmentResult r join fetch r.student join fetch r.assessment a "
            + "join fetch a.course c join fetch c.instructor where r.id = :id")
    Optional<AssessmentResult> findDetailedById(@Param("id") Long id);

    @Query("select r from AssessmentResult r join fetch r.assessment a join fetch a.course c "
            + "where r.student.id = :studentId order by c.id asc, a.id asc")
    List<AssessmentResult> findByStudentWithAssessment(@Param("studentId") Long studentId);

    @Query("select r from AssessmentResult r join fetch r.assessment a "
            + "where r.student.id = :studentId and a.course.id = :courseId order by a.id asc")
    List<AssessmentResult> findByStudentAndCourse(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    @Query("select r from AssessmentResult r join fetch r.assessment a "
            + "where a.course.id in :courseIds order by a.id asc")
    List<AssessmentResult> findByCourses(@Param("courseIds") Collection<Long> courseIds);

    @Query("select r from AssessmentResult r join fetch r.assessment a join fetch a.course "
            + "where a.id = :assessmentId and r.student.id = :studentId")
    Optional<AssessmentResult> findByAssessmentAndStudent(@Param("assessmentId") Long assessmentId,
                                                          @Param("studentId") Long studentId);

    @Query("select case when count(r) > 0 then true else false end from AssessmentResult r "
            + "where r.student.id = :studentId and r.assessment.id = :assessmentId")
    boolean existsByStudentAndAssessment(@Param("studentId") Long studentId, @Param("assessmentId") Long assessmentId);

    long countByStatus(AssessmentStatus status);
}
