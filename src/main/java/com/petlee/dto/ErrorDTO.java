package com.petlee.dto;

/**
 * The body of every error response: a user-facing {@code message} and a short machine
 * {@code code} such as {@code USERNAME_TAKEN}.
 *
 * <p>Never a stack trace and never a SQL fragment.
 */
public record ErrorDTO(String code, String message) { }
