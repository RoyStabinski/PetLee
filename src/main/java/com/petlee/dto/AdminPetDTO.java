package com.petlee.dto;

import com.petlee.model.Pet;

import java.io.Serializable;
import java.time.LocalDateTime;

/** A pet as the admin panel lists it: the public fields plus the owner and creation date. */
public record AdminPetDTO(Long id, String name, String status, String categoryName,
                          String imageUrl, String ownerName, LocalDateTime createdAt)
        implements Serializable {

    public static AdminPetDTO of(Pet p) {
        return new AdminPetDTO(p.getPetId(), p.getPetName(), p.getStatus().name(),
                p.getCategory().getCategoryName(), p.getMainImageUrl(),
                p.getOwner() == null ? null : p.getOwner().getFullName(), p.getCreatedAt());
    }
}
