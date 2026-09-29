package com.lms.tracker.exception;

/** 429 - brute-force protection on login. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
