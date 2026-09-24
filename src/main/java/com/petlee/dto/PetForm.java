package com.petlee.dto;

import jakarta.validation.constraints.*;

/**
 * The body of {@code POST /api/pets} and {@code PUT /api/pets/{id}}. The enums stay strings so
 * an unknown value is a 400 the caller can read, not a deserialisation failure.
 */
public record PetForm(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String breed,
        @Min(0) @Max(50) Integer age,
        @NotNull @Pattern(regexp = "SMALL|MEDIUM|LARGE") String size,
        @NotNull @Pattern(regexp = "MALE|FEMALE") String gender,
        @NotBlank @Size(max = 255) String shortDesc,
        String longDesc,
        @NotNull Integer categoryId) { }
