package com.petlee.dto;

import com.petlee.model.Pet;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A listing as the admin table shows it: {@link PetDTO}'s identity fields plus who posted it and
 * when. Kept apart from {@code PetDTO} because the public gallery uses that one, and a guest has
 * no business seeing an owner's name. {@code imageUrl} is the main image, or null.
 */
public record AdminPetDTO(Long id, String name, String status, String categoryName,
                          String imageUrl, String ownerName, LocalDateTime createdAt)
        implements Serializable {

    public static AdminPetDTO of(Pet p) {
        return new AdminPetDTO(p.getPetId(), p.getPetName(), p.getStatus().name(),
                p.getCategory().getCategoryName(), p.getMainImageUrl(),
                p.getOwner() == null ? null : p.getOwner().getFullName(), p.getCreatedAt());
    }
}
