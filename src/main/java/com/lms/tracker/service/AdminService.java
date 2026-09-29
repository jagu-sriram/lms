package com.lms.tracker.service;

import com.lms.tracker.dto.request.CreateUserRequest;
import com.lms.tracker.dto.response.UserResponse;
import com.lms.tracker.security.AuthenticatedUser;

import java.util.List;

public interface AdminService {

    List<UserResponse> listUsers(AuthenticatedUser actor);

    UserResponse createUser(CreateUserRequest request, AuthenticatedUser actor);

    UserResponse updateUserStatus(Long userId, boolean enabled, AuthenticatedUser actor);
}
