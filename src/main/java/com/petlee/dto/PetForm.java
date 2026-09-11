package com.petlee.dto;

import jakarta.validation.constraints.*;

/**
 * The body of {@code POST /api/pets} and {@code PUT /api/pets/{id}}.
 *
 * <p>{@code size} and {@code gender} arrive as strings and stay strings here. {@code PetService}
 * parses them, so an unknown value becomes a 400 it can explain rather than a deserialisation
 * failure the caller cannot read. {@code categoryId} is resolved to a {@code Category} there too —
 * a form never becomes an entity in this package.
 */
public record PetForm(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String breed,
        @Min(0) @Max(50) Integer age,
        @NotNull @Pattern(regexp = "SMALL|MEDIUM|LARGE") String size,
        @NotNull @Pattern(regexp = "MALE|FEMALE") String gender,
        @Size(max = 255) String shortDesc,
        String longDesc,
        @NotNull Integer categoryId) { }
