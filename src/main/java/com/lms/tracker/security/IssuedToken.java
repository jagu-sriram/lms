package com.lms.tracker.security;

import java.time.Instant;

public record IssuedToken(String token, String tokenId, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[tokenId=" + tokenId + ", expiresAt=" + expiresAt + ", token=****]";
    }
}
