package com.petlee.dto;

import com.petlee.model.Pet;

/**
 * A pet as the gallery lists it — the main image only, no owner contact details.
 *
 * <p>{@code size}, {@code gender} and {@code status} are the contract's uppercase enum strings,
 * held as {@code String} so an unrecognised value is a mapping decision rather than a
 * deserialisation failure.
 */
public record PetDTO(Long id, String name, String shortDesc, Integer age, String size,
                     String gender, String status, String categoryName, String imageUrl) {

    public static PetDTO of(Pet p) {
        return new PetDTO(p.getPetId(), p.getPetName(), p.getShortDesc(), p.getAge(),
                p.getSize().name(), p.getGender().name(), p.getStatus().name(),
                p.getCategory().getCategoryName(), p.getImageUrl());
    }
}
