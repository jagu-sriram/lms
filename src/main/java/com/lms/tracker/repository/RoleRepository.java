package com.lms.tracker.repository;

import com.lms.tracker.entity.Role;
import com.lms.tracker.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
