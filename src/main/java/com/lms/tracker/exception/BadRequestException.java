package com.lms.tracker.exception;

/** 400 - the input is syntactically valid but violates a business rule. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
