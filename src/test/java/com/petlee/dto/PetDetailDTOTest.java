package com.petlee.dto;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Specification §6: "Unregistered clients can view details of pets offered for adoption without
 * seeing private contact information." {@link PetDetailDTO#of(Pet, boolean)} is the REST-side
 * gate for that rule.
 */
class PetDetailDTOTest {

    private static Pet petWithOwner() {
        User owner = new User();
        owner.setUserId(1L);
        owner.setFullName("Dana Owner");
        owner.setPhoneNumber("050-1110001");
        owner.setEmail("dana.owner@petlee.demo");

        Category category = new Category("Dogs");
        category.setCategoryId(1);

        Pet pet = new Pet();
        pet.setPetId(7L);
        pet.setPetName("Rex");
        pet.setSize(Pet.PetSize.MEDIUM);
        pet.setGender(Pet.PetGender.MALE);
        pet.setStatus(Pet.PetStatus.AVAILABLE);
        pet.setCategory(category);
        pet.setOwner(owner);
        return pet;
    }

    @Test
    void aGuestSeesNoOwnerContactDetails() {
        PetDetailDTO dto = PetDetailDTO.of(petWithOwner(), false);

        assertEquals("Rex", dto.name());
        assertNull(dto.ownerName());
        assertNull(dto.ownerPhone());
        assertNull(dto.ownerEmail());
    }

    @Test
    void anAuthenticatedCallerSeesTheOwnersContactDetails() {
        PetDetailDTO dto = PetDetailDTO.of(petWithOwner(), true);

        assertEquals("Dana Owner", dto.ownerName());
        assertEquals("050-1110001", dto.ownerPhone());
        assertEquals("dana.owner@petlee.demo", dto.ownerEmail());
    }
}
