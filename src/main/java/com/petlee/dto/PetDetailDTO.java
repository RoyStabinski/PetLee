package com.petlee.dto;

import com.petlee.model.Pet;

import java.io.Serializable;
import java.util.List;

/**
 * A pet in full. The three owner fields are null for a caller who is not logged in.
 * {@code version} is what a client sends back with {@code PUT /api/pets/{id}?version=},
 * so a save based on stale data is refused rather than silently overwriting a newer one.
 * {@code categoryId} and {@code ownedByCaller} are for an edit form: the value to preselect,
 * and whether to offer the form at all. {@code ownedByCaller} is a convenience for the client;
 * the write endpoints still check ownership themselves. {@code imageUrl} is the main image;
 * {@code images} lists every photograph, main included, oldest first.
 */
public record PetDetailDTO(Long id, String name, String breed, Integer age, String size,
                           String gender, String shortDesc, String longDesc, String status,
                           Integer categoryId, String categoryName, String imageUrl,
                           String ownerName, String ownerPhone, String ownerEmail,
                           boolean ownedByCaller, Long version, List<PetImageDTO> images)
        implements Serializable {

    /**
     * @param p             the pet
     * @param authenticated whether the caller is logged in
     * @param ownedByCaller whether the caller is the pet's owner; false for a guest
     * @return the pet; a guest gets the same shape with three nulls, not a different one
     */
    public static PetDetailDTO of(Pet p, boolean authenticated, boolean ownedByCaller) {
        return new PetDetailDTO(p.getPetId(), p.getPetName(), p.getBreed(), p.getAge(),
                p.getSize().name(), p.getGender().name(), p.getShortDesc(), p.getLongDesc(),
                p.getStatus().name(), p.getCategory().getCategoryId(),
                p.getCategory().getCategoryName(), p.getMainImageUrl(),
                authenticated ? p.getOwner().getFullName() : null,
                authenticated ? p.getOwner().getPhoneNumber() : null,
                authenticated ? p.getOwner().getEmail() : null,
                ownedByCaller, p.getVersion(),
                p.getImages().stream().map(PetImageDTO::of).toList());
    }
}