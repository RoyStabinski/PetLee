package com.petlee.service;

/** One exception for the whole application, carrying the HTTP status the boundary should use. */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final int status;

    public AppException(int status, String message) {
        super(message);
        this.status = status;
    }

    public AppException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
