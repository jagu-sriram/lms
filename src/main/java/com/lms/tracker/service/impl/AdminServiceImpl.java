package com.lms.tracker.service.impl;

import com.lms.tracker.dto.request.CreateUserRequest;
import com.lms.tracker.dto.response.UserResponse;
import com.lms.tracker.entity.Role;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.exception.BadRequestException;
import com.lms.tracker.exception.ConflictException;
import com.lms.tracker.exception.ResourceNotFoundException;
import com.lms.tracker.repository.RoleRepository;
import com.lms.tracker.repository.UserRepository;
import com.lms.tracker.security.AccessPolicy;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.service.AdminService;
import com.lms.tracker.util.DtoMapper;
import com.lms.tracker.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessPolicy accessPolicy;

    public AdminServiceImpl(UserRepository userRepository,
                            RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder,
                            AccessPolicy accessPolicy) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public List<UserResponse> listUsers(AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "list users", RoleName.ADMIN);
        return userRepository.findAllWithRole().stream().map(DtoMapper::toUser).toList();
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request, AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "create users", RoleName.ADMIN);
        String email = AuthServiceImpl.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new IllegalStateException("Role " + request.role() + " is not configured"));
        User user = userRepository.save(new User(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), role));
        log.info("AUDIT User created by admin: newUserId={} role={} email={} by adminId={}",
                user.getId(), role.getName(), LogSanitizer.maskEmail(email), actor.getId());
        return DtoMapper.toUser(user);
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(Long userId, boolean enabled, AuthenticatedUser actor) {
        accessPolicy.requireRole(actor, "enable or disable users", RoleName.ADMIN);
        if (userId.equals(actor.getId()) && !enabled) {
            throw new BadRequestException("You cannot disable your own account");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setEnabled(enabled);
        user = userRepository.saveAndFlush(user);
        log.info("AUDIT User {}: userId={} by adminId={}", enabled ? "enabled" : "disabled", userId, actor.getId());
        return DtoMapper.toUser(user);
    }
}
