package com.lms.tracker.security;

import java.time.Instant;

/** The verified claims we rely on: who (subject) and which token (jti, for logout). */
public record TokenClaims(Long userId, String tokenId, Instant expiresAt) {
}
