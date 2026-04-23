package com.bayport.exception;

/**
 * Thrown when a requested resource cannot be located.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

