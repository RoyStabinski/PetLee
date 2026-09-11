package com.petlee.dto;

import jakarta.validation.constraints.*;

/**
 * The body of {@code POST /api/users/register}. Carries a plaintext password inbound only —
 * it is hashed by {@code UserService} and never stored, logged or echoed back.
 */
public record RegisterForm(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$",
                message = "Username must be 3-20 characters: letters, digits and underscore")
        String username,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
        String password,
        @NotBlank @Size(max = 50) String fullName,
        @NotBlank @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 100) String region) { }
