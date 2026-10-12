package com.example.docmind.exception;

/** Tài nguyên không tồn tại -> 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
