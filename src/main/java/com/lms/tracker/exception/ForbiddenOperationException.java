package com.lms.tracker.exception;

/**
 * 403 - the caller is authenticated but not allowed to act on this specific object
 * (object-level authorization / IDOR protection).
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
