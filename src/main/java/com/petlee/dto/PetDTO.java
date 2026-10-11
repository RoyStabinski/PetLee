package com.petlee.dto;

import com.petlee.model.Pet;
import com.petlee.model.PetImage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A pet as the gallery lists it: no owner contact details, enums as their constant names.
 * {@code imageUrl} is the main image, or null when the pet has none. {@code images} lists every
 * photograph with its focal point, for the gallery card's carousel, the main one first and the
 * rest oldest first; empty when the pet has none. {@code version} is what the owner sends back
 * with {@code PUT /api/pets/{id}/status?version=}.
 */
public record PetDTO(Long id, String name, String shortDesc, Integer age, String size,
                     String gender, String status, String categoryName, String imageUrl,
                     List<PhotoDTO> images, Long version)
        implements Serializable {

    public static PetDTO of(Pet p) {
        return new PetDTO(p.getPetId(), p.getPetName(), p.getShortDesc(), p.getAge(),
                p.getSize().name(), p.getGender().name(), p.getStatus().name(),
                p.getCategory().getCategoryName(), p.getMainImageUrl(), photosOf(p),
                p.getVersion());
    }

    /** The images are already loaded: the gallery query fetches them, as getMainImageUrl needs. */
    private static List<PhotoDTO> photosOf(Pet p) {
        List<PhotoDTO> photos = new ArrayList<>();
        p.getImages().stream().filter(PetImage::isMain).forEach(image -> photos.add(PhotoDTO.of(image)));
        p.getImages().stream().filter(image -> !image.isMain()).forEach(image -> photos.add(PhotoDTO.of(image)));
        return photos;
    }
}
