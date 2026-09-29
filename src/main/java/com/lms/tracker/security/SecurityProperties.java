package com.lms.tracker.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Binds the app.security.* properties. Invalid configuration fails fast at start-up.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Min(4) @Max(31) int bcryptStrength,
        @Valid @NotNull Login login
) {

    public record Jwt(
            @NotBlank String secret,
            @NotNull Duration expiration,
            @NotBlank String issuer
    ) {
        @Override
        public String toString() {
            return "Jwt[issuer=" + issuer + ", expiration=" + expiration + ", secret=****]";
        }
    }

    public record Login(
            @Min(1) int maxFailedAttempts,
            @NotNull Duration lockDuration
    ) {
    }
}
