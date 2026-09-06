package com.petlee.dto;

import jakarta.validation.constraints.NotBlank;

/** The body of {@code POST /api/auth/login}: {@code username}, {@code password}. */
public record LoginForm(@NotBlank String username, @NotBlank String password) { }
