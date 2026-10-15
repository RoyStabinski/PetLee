package com.petlee.dto;

import com.petlee.model.Pet;

import java.io.Serializable;

/** A pet as the gallery shows it: no breed, long description or owner details. */
public record PetDTO(Long id, String name, String shortDesc, Integer age, String size,
                     String gender, String status, String categoryName, String imageUrl,
                     Long version) implements Serializable {

    public static PetDTO of(Pet p) {
        return new PetDTO(p.getPetId(), p.getPetName(), p.getShortDesc(), p.getAge(),
                p.getSize().name(), p.getGender().name(), p.getStatus().name(),
                p.getCategory().getCategoryName(), p.getMainImageUrl(), p.getVersion());
    }
}
