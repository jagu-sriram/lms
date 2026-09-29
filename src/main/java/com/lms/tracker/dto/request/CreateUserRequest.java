package com.lms.tracker.dto.request;

import com.lms.tracker.entity.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin-only: create a user with any role (e.g. an INSTRUCTOR). */
public record CreateUserRequest(
        @NotBlank(message = "is required")
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
        String fullName,

        @NotBlank(message = "is required")
        @Email(message = "must be a valid email address")
        @Size(max = 150, message = "must be at most 150 characters")
        String email,

        @NotBlank(message = "is required")
        @Pattern(regexp = ValidationPatterns.STRONG_PASSWORD, message = ValidationPatterns.STRONG_PASSWORD_MESSAGE)
        String password,

        @NotNull(message = "is required (STUDENT, INSTRUCTOR or ADMIN)")
        RoleName role
) {
    @Override
    public String toString() {
        return "CreateUserRequest[email=" + email + ", role=" + role + ", password=****]";
    }
}
