package com.lms.tracker.repository;

import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select u from User u join fetch u.role order by u.id asc")
    List<User> findAllWithRole();

    @Query("select count(u) from User u where u.role.name = :roleName")
    long countByRoleName(@Param("roleName") RoleName roleName);
}
