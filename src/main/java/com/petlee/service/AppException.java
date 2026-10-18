package com.petlee.service;

/**
 * One exception for the whole application, carrying the HTTP status the boundary should use and,
 * where a client needs to tell two failures with the same status apart, a code for the body.
 */
public class AppException extends RuntimeException {

    /** The code of an error that carries no more specific one. */
    public static final String DEFAULT_CODE = "ERROR";

    /** A 409 for a save based on a stale version, as opposed to any other conflict. */
    public static final String STALE_VERSION = "STALE_VERSION";

    private static final long serialVersionUID = 1L;
    private final int status;
    private final String code;

    /**
     * @param status  the HTTP status for the response
     * @param message the text shown to the user
     */
    public AppException(int status, String message) {
        this(status, DEFAULT_CODE, message, null);
    }

    /**
     * @param status  the HTTP status for the response
     * @param message the text shown to the user
     * @param cause   the underlying failure, kept for the log
     */
    public AppException(int status, String message, Throwable cause) {
        this(status, DEFAULT_CODE, message, cause);
    }

    /**
     * @param status  the HTTP status for the response
     * @param code    the code for the error body, such as {@link #STALE_VERSION}
     * @param message the text shown to the user
     * @param cause   the underlying failure, or null
     */
    public AppException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    /** @return the HTTP status the boundary answers with */
    public int getStatus() {
        return status;
    }

    /** @return the code for the error body, {@value #DEFAULT_CODE} unless a caller chose one */
    public String getCode() {
        return code;
    }
}
