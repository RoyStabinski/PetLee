package com.petlee.dto;

import com.petlee.model.PetImage;

import java.io.Serializable;

/**
 * One photograph on a gallery card: its URL under {@code /images/} and its focal point, in
 * percent of the width and the height, for CSS {@code object-position}.
 */
public record PhotoDTO(String url, int focusX, int focusY) implements Serializable {

    public static PhotoDTO of(PetImage image) {
        return new PhotoDTO(image.getImageUrl(), image.getFocusX(), image.getFocusY());
    }
}
