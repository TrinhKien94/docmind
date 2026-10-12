package com.example.docmind.exception;

/** File không hợp lệ: magic-byte không khớp loại được hỗ trợ -> 415. */
public class UnsupportedFileTypeException extends RuntimeException {
    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
