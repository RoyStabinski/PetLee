package com.petlee.dto;

import com.petlee.model.PetImage;

import java.io.Serializable;

/** One photograph of a pet: its id, its URL under {@code /images/}, and whether it is the main one. */
public record PetImageDTO(Long id, String url, boolean main) implements Serializable {

    public static PetImageDTO of(PetImage image) {
        return new PetImageDTO(image.getImageId(), image.getImageUrl(), image.isMain());
    }
}
