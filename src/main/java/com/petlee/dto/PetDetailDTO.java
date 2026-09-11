package com.petlee.dto;

import com.petlee.model.Pet;

/**
 * A pet as the details page shows it: every field, its photograph, and the owner's contact
 * details.
 *
 * <p>The three owner fields are {@code null} for a caller who is not logged in — see
 * {@link #of(Pet, boolean)}, which is the only place that decision is made on the REST side.
 */
public record PetDetailDTO(Long id, String name, String breed, Integer age, String size,
                           String gender, String shortDesc, String longDesc, String status,
                           String categoryName, String imageUrl,
                           String ownerName, String ownerPhone, String ownerEmail) {

    /**
     * Contact details are populated only for an authenticated caller (specification §6).
     * A guest receives the same shape with three nulls, not a different shape.
     */
    public static PetDetailDTO of(Pet p, boolean authenticated) {
        return new PetDetailDTO(p.getPetId(), p.getPetName(), p.getBreed(), p.getAge(),
                p.getSize().name(), p.getGender().name(), p.getShortDesc(), p.getLongDesc(),
                p.getStatus().name(), p.getCategory().getCategoryName(), p.getImageUrl(),
                authenticated ? p.getOwner().getFullName() : null,
                authenticated ? p.getOwner().getPhoneNumber() : null,
                authenticated ? p.getOwner().getEmail() : null);
    }
}
