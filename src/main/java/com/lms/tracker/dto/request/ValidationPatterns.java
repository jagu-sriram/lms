package com.lms.tracker.dto.request;

/**
 * Shared validation rules so the same field is validated identically everywhere.
 */
public final class ValidationPatterns {

    /** Letters (any language), spaces, apostrophes, dots and hyphens. */
    public static final String PERSON_NAME = "^[\\p{L}][\\p{L} .'-]{1,99}$";
    public static final String PERSON_NAME_MESSAGE =
            "must be 2-100 characters and contain only letters, spaces, dots, apostrophes or hyphens";

    /** At least one upper-case, one lower-case, one digit and one special character, no whitespace. */
    public static final String STRONG_PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S{8,72}$";
    public static final String STRONG_PASSWORD_MESSAGE =
            "must be 8-72 characters with upper-case, lower-case, digit and special character, and no spaces";

    private ValidationPatterns() {
    }
}
