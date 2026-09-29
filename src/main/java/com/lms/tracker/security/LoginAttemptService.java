package com.lms.tracker.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection: after N failed logins for the same (email, client IP) pair,
 * further attempts are refused with 429 for the lock duration.
 * Keyed on the pair (not just the email) so an attacker cannot lock out a victim from everywhere.
 * In-memory is sufficient for a single-instance MVP; a cluster would use a shared store (e.g. Redis).
 */
@Service
public class LoginAttemptService {

    private record Attempt(int failures, Instant windowStart, Instant lockedUntil) {
    }

    private final int maxFailedAttempts;
    private final Duration lockDuration;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(SecurityProperties properties) {
        this.maxFailedAttempts = properties.login().maxFailedAttempts();
        this.lockDuration = properties.login().lockDuration();
    }

    public static String key(String email, String clientIp) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return normalized + "|" + clientIp;
    }

    public boolean isBlocked(String key) {
        Attempt attempt = attempts.get(key);
        return attempt != null && attempt.lockedUntil() != null && Instant.now().isBefore(attempt.lockedUntil());
    }

    public void recordFailure(String key) {
        Instant now = Instant.now();
        attempts.compute(key, (k, current) -> {
            boolean startNewWindow = current == null
                    || (current.lockedUntil() != null && !now.isBefore(current.lockedUntil()))
                    || now.isAfter(current.windowStart().plus(lockDuration));
            int failures = startNewWindow ? 1 : current.failures() + 1;
            Instant windowStart = startNewWindow ? now : current.windowStart();
            Instant lockedUntil = failures >= maxFailedAttempts ? now.plus(lockDuration) : null;
            return new Attempt(failures, windowStart, lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    @Scheduled(fixedDelayString = "PT5M")
    public void evictStaleEntries() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            Instant relevantUntil = a.lockedUntil() != null ? a.lockedUntil() : a.windowStart().plus(lockDuration);
            return now.isAfter(relevantUntil);
        });
    }
}
