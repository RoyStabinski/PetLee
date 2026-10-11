package com.petlee.dto;

import com.petlee.model.PetImage;

import java.io.Serializable;

/**
 * One photograph of a pet: its id, its URL under {@code /images/}, whether it is the main one,
 * and its focal point in percent of the width and the height.
 */
public record PetImageDTO(Long id, String url, boolean main, int focusX, int focusY)
        implements Serializable {

    public static PetImageDTO of(PetImage image) {
        return new PetImageDTO(image.getImageId(), image.getImageUrl(), image.isMain(),
                image.getFocusX(), image.getFocusY());
    }
}
