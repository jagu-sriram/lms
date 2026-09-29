package com.lms.tracker.security;

import java.time.Instant;

/** Attached to the Authentication so logout knows which token to revoke. */
public record TokenDetails(String tokenId, Instant expiresAt) {
}
