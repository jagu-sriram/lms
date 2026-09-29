package com.lms.tracker.repository;

import com.lms.tracker.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("select c from Course c join fetch c.instructor where c.id = :id")
    Optional<Course> findWithInstructorById(@Param("id") Long id);

    @Query("select c from Course c join fetch c.instructor order by c.title asc")
    List<Course> findAllWithInstructor();

    @Query("select c from Course c join fetch c.instructor i where i.id = :instructorId order by c.title asc")
    List<Course> findByInstructorId(@Param("instructorId") Long instructorId);
}
