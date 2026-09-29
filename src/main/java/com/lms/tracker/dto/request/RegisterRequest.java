package com.lms.tracker.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-registration. There is deliberately no "role" field: every self-registered
 * account is a STUDENT, and any extra JSON field is rejected (mass-assignment protection).
 */
public record RegisterRequest(
        @NotBlank(message = "is required")
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
        String fullName,

        @NotBlank(message = "is required")
        @Email(message = "must be a valid email address")
        @Size(max = 150, message = "must be at most 150 characters")
        String email,

        @NotBlank(message = "is required")
        @Pattern(regexp = ValidationPatterns.STRONG_PASSWORD, message = ValidationPatterns.STRONG_PASSWORD_MESSAGE)
        String password
) {
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=****]";
    }
}
