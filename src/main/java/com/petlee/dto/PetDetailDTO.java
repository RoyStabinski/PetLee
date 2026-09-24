package com.petlee.dto;

import com.petlee.model.Pet;

/**
 * A pet in full. The three owner fields are null for a caller who is not logged in.
 * {@code version} is what a client sends back with {@code PUT /api/pets/{id}?version=},
 * so a save based on stale data is refused rather than silently overwriting a newer one.
 */
public record PetDetailDTO(Long id, String name, String breed, Integer age, String size,
                           String gender, String shortDesc, String longDesc, String status,
                           String categoryName, String imageUrl,
                           String ownerName, String ownerPhone, String ownerEmail,
                           Long version) {

    /**
     * @param p             the pet
     * @param authenticated whether the caller is logged in
     * @return the pet; a guest gets the same shape with three nulls, not a different one
     */
    public static PetDetailDTO of(Pet p, boolean authenticated) {
        return new PetDetailDTO(p.getPetId(), p.getPetName(), p.getBreed(), p.getAge(),
                p.getSize().name(), p.getGender().name(), p.getShortDesc(), p.getLongDesc(),
                p.getStatus().name(), p.getCategory().getCategoryName(), p.getImageUrl(),
                authenticated ? p.getOwner().getFullName() : null,
                authenticated ? p.getOwner().getPhoneNumber() : null,
                authenticated ? p.getOwner().getEmail() : null,
                p.getVersion());
    }
}