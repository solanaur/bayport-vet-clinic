package com.bayport.exception;

/**
 * Thrown when a request cannot be completed due to a conflict with the current state.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}

