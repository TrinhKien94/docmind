package com.example.docmind.exception;

/** Lỗi khi gọi doc-service-py (parser Python) -> 502. */
public class DocServiceException extends RuntimeException {
    public DocServiceException(String message) {
        super(message);
    }

    public DocServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
