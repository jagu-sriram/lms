package com.lms.tracker.util;

/**
 * Helpers that make user-controlled values safe to write to logs:
 * strips CR/LF and other control characters (log injection) and masks e-mail addresses (PII).
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        return truncate(value.replaceAll("[\\p{Cntrl}]", "_"), 200);
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    /** "student.one@lms.local" -> "st***@lms.local" */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<none>";
        }
        String cleaned = clean(email.trim());
        int at = cleaned.indexOf('@');
        if (at <= 0) {
            return cleaned.length() <= 2 ? "***" : cleaned.substring(0, 2) + "***";
        }
        String local = cleaned.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + cleaned.substring(at);
    }
}
