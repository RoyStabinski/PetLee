package com.petlee.dto;

import jakarta.validation.constraints.*;

import java.io.Serializable;

/**
 * The body of {@code PUT /api/users/me}: the fields a member may change about themselves, with
 * the same constraints as {@link RegisterForm}. There is no username field, so the username
 * cannot change, and no id, since the user is always the caller.
 */
public record ProfileForm(
        @NotBlank @Size(max = 50) String fullName,
        @NotBlank @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 100) String region) implements Serializable { }
