package com.lms.tracker.repository;

import com.lms.tracker.entity.Enrollment;
import com.lms.tracker.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Query("select case when count(e) > 0 then true else false end from Enrollment e "
            + "where e.student.id = :studentId and e.course.id = :courseId and e.status = :status")
    boolean existsByStudentAndCourseAndStatus(@Param("studentId") Long studentId,
                                              @Param("courseId") Long courseId,
                                              @Param("status") EnrollmentStatus status);

    @Query("select e from Enrollment e where e.student.id = :studentId and e.course.id = :courseId")
    Optional<Enrollment> findByStudentAndCourse(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    @Query("select e from Enrollment e join fetch e.course c join fetch c.instructor "
            + "where e.student.id = :studentId and e.status = :status order by e.createdAt asc")
    List<Enrollment> findByStudentWithCourse(@Param("studentId") Long studentId, @Param("status") EnrollmentStatus status);

    @Query("select e from Enrollment e join fetch e.student s "
            + "where e.course.id = :courseId and e.status = :status order by s.fullName asc")
    List<Enrollment> findByCourseWithStudent(@Param("courseId") Long courseId, @Param("status") EnrollmentStatus status);

    @Query("select e from Enrollment e join fetch e.student "
            + "where e.course.id in :courseIds and e.status = :status")
    List<Enrollment> findByCoursesWithStudent(@Param("courseIds") Collection<Long> courseIds,
                                              @Param("status") EnrollmentStatus status);

    @Query("select e from Enrollment e join fetch e.student join fetch e.course order by e.createdAt desc")
    List<Enrollment> findAllWithStudentAndCourse();

    @Query("select e.course.id, count(e) from Enrollment e where e.status = :status group by e.course.id")
    List<Object[]> countPerCourse(@Param("status") EnrollmentStatus status);

    @Query("select count(e) from Enrollment e where e.course.id = :courseId and e.status = :status")
    long countByCourseAndStatus(@Param("courseId") Long courseId, @Param("status") EnrollmentStatus status);

    long countByStatus(EnrollmentStatus status);
}
