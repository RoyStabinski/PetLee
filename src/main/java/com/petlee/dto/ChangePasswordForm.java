package com.petlee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * The body of {@code PUT /api/users/me/password}. Carries plaintext passwords inbound only —
 * the new one is hashed by {@code UserService}, and neither is stored, logged or echoed back.
 */
public record ChangePasswordForm(
        @NotBlank(message = "Enter your current password")
        String currentPassword,
        @NotBlank @Size(min = 8, message = "The new password must be at least 8 characters")
        String newPassword) implements Serializable { }
