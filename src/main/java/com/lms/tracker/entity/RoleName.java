package com.lms.tracker.entity;

public enum RoleName {
    STUDENT,
    INSTRUCTOR,
    ADMIN;

    /** Spring Security authority name, e.g. ROLE_STUDENT. */
    public String authority() {
        return "ROLE_" + name();
    }
}
