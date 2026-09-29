package com.lms.tracker.exception;

/** 409 - the request conflicts with the current state (duplicate, wrong workflow state). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
