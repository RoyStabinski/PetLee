package com.petlee.dto;

import com.petlee.model.Pet;

/** A pet as the gallery lists it: no owner contact details, enums as the contract's strings. */
public record PetDTO(Long id, String name, String shortDesc, Integer age, String size,
                     String gender, String status, String categoryName, String imageUrl) {

    public static PetDTO of(Pet p) {
        return new PetDTO(p.getPetId(), p.getPetName(), p.getShortDesc(), p.getAge(),
                p.getSize().name(), p.getGender().name(), p.getStatus().name(),
                p.getCategory().getCategoryName(), p.getImageUrl());
    }
}
