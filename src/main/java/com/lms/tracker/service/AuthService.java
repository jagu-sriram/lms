package com.lms.tracker.service;

import com.lms.tracker.dto.request.LoginRequest;
import com.lms.tracker.dto.request.RegisterRequest;
import com.lms.tracker.dto.response.AuthResponse;
import com.lms.tracker.dto.response.UserResponse;
import com.lms.tracker.security.AuthenticatedUser;
import com.lms.tracker.security.TokenDetails;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request, String clientIp);

    void logout(AuthenticatedUser actor, TokenDetails tokenDetails);

    UserResponse currentUser(AuthenticatedUser actor);
}
