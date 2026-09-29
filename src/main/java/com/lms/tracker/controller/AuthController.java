package com.lms.tracker.controller;

import com.lms.tracker.dto.request.LoginRequest;
import com.lms.tracker.dto.request.RegisterRequest;
import com.lms.tracker.dto.response.AuthResponse;
import com.lms.tracker.dto.response.UserResponse;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.security.TokenDetails;
import com.lms.tracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Public. Always creates a STUDENT account. */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** Public. Returns a bearer token. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // getRemoteAddr is used deliberately; X-Forwarded-For is client-controlled unless a trusted proxy sets it
        return authService.login(request, httpRequest.getRemoteAddr());
    }

    /** Revokes the token used for this request. */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser actor, Authentication authentication) {
        TokenDetails details = authentication.getDetails() instanceof TokenDetails td ? td : null;
        authService.logout(actor, details);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser actor) {
        return authService.currentUser(actor);
    }
}
