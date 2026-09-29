package com.lms.tracker.service.impl;

import com.lms.tracker.dto.request.LoginRequest;
import com.lms.tracker.dto.request.RegisterRequest;
import com.lms.tracker.dto.response.AuthResponse;
import com.lms.tracker.dto.response.UserResponse;
import com.lms.tracker.entity.Role;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.exception.BadRequestException;
import com.lms.tracker.exception.ConflictException;
import com.lms.tracker.exception.ResourceNotFoundException;
import com.lms.tracker.exception.TooManyRequestsException;
import com.lms.tracker.repository.RoleRepository;
import com.lms.tracker.repository.UserRepository;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.security.IssuedToken;
import com.lms.tracker.security.JwtService;
import com.lms.tracker.security.LoginAttemptService;
import com.lms.tracker.security.TokenDetails;
import com.lms.tracker.security.TokenRevocationService;
import com.lms.tracker.service.AuthService;
import com.lms.tracker.util.DtoMapper;
import com.lms.tracker.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final TokenRevocationService tokenRevocationService;

    public AuthServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtService jwtService,
                           LoginAttemptService loginAttemptService,
                           TokenRevocationService tokenRevocationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.tokenRevocationService = tokenRevocationService;
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            log.info("Registration rejected, email already in use: {}", LogSanitizer.maskEmail(email));
            throw new ConflictException("An account with this email already exists");
        }
        // Self-registration can only ever create a STUDENT (no privilege escalation)
        Role studentRole = roleRepository.findByName(RoleName.STUDENT)
                .orElseThrow(() -> new IllegalStateException("Role STUDENT is not configured"));
        User user = userRepository.save(new User(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), studentRole));
        log.info("User registered: userId={} role={}", user.getId(), RoleName.STUDENT);
        return DtoMapper.toUser(user);
    }

    @Override
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        String maskedEmail = LogSanitizer.maskEmail(email);
        String attemptKey = LoginAttemptService.key(email, clientIp);

        if (loginAttemptService.isBlocked(attemptKey)) {
            log.warn("SECURITY Login blocked after repeated failures: email={} ip={}", maskedEmail, LogSanitizer.clean(clientIp));
            throw new TooManyRequestsException("Too many failed login attempts. Please try again later");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(attemptKey);
            log.warn("SECURITY Login failed: email={} ip={} reason={}", maskedEmail,
                    LogSanitizer.clean(clientIp), ex.getClass().getSimpleName());
            // Same message for unknown email, wrong password and disabled account (no user enumeration)
            throw new BadCredentialsException("Invalid email or password");
        }

        loginAttemptService.recordSuccess(attemptKey);
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        IssuedToken token = jwtService.issue(principal);
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));
        log.info("Login successful: userId={} role={}", principal.getId(), principal.getRole());
        return new AuthResponse(token.token(), "Bearer", token.expiresAt(), DtoMapper.toUser(user));
    }

    @Override
    public void logout(AuthenticatedUser actor, TokenDetails tokenDetails) {
        if (tokenDetails == null) {
            throw new BadRequestException("No bearer token to revoke");
        }
        tokenRevocationService.revoke(tokenDetails.tokenId(), actor.getId(), tokenDetails.expiresAt());
        log.info("Logout: userId={} token revoked", actor.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(AuthenticatedUser actor) {
        return userRepository.findById(actor.getId())
                .map(DtoMapper::toUser)
                .orElseThrow(() -> new ResourceNotFoundException("User", actor.getId()));
    }
}
