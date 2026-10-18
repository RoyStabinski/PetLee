package com.petlee.dto;

import java.io.Serializable;

/** The body of every error response. Never a stack trace and never a SQL fragment. */
public record ErrorDTO(String code, String message) implements Serializable { }
