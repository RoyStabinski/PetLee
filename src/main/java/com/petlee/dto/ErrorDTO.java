package com.petlee.dto;

/**
 * The body of every error response: a user-facing {@code message} and a short machine
 * {@code code} such as {@code USERNAME_TAKEN}.
 *
 * <p>Never a stack trace and never a SQL fragment — both fields are written by T-19, which is the
 * only producer.
 */
public class ErrorDTO {

    private String message;
    private String code;

    public ErrorDTO() {
    }

    public ErrorDTO(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
