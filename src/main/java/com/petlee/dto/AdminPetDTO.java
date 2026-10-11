package com.petlee.dto;

import com.petlee.model.Pet;
import com.petlee.model.PetImage;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A listing as the admin table shows it: {@link PetDTO}'s identity fields plus who posted it and
 * when. Kept apart from {@code PetDTO} because the public gallery uses that one, and a guest has
 * no business seeing an owner's name. {@code imageUrl} is the main image, or null;
 * {@code imageFocusX} and {@code imageFocusY} are its focal point in percent, 50/50 without one.
 */
public record AdminPetDTO(Long id, String name, String status, String categoryName,
                          String imageUrl, int imageFocusX, int imageFocusY, String ownerName,
                          LocalDateTime createdAt)
        implements Serializable {

    public static AdminPetDTO of(Pet p) {
        PetImage main = p.getMainImage();
        return new AdminPetDTO(p.getPetId(), p.getPetName(), p.getStatus().name(),
                p.getCategory().getCategoryName(), main == null ? null : main.getImageUrl(),
                main == null ? 50 : main.getFocusX(), main == null ? 50 : main.getFocusY(),
                p.getOwner() == null ? null : p.getOwner().getFullName(), p.getCreatedAt());
    }
}
