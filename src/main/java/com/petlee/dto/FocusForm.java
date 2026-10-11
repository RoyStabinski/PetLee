package com.petlee.dto;

import java.io.Serializable;

/**
 * The body of {@code PUT /api/pets/{id}/images/{imageId}/focus} and
 * {@code PUT /api/users/me/avatar/focus}: a photo's focal point, in whole percent of its width
 * ({@code x}) and height ({@code y}), each 0 to 100. Integer, not int, so a missing value arrives
 * as null and is refused rather than read as 0.
 */
public record FocusForm(Integer x, Integer y) implements Serializable { }
