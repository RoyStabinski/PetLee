package com.petlee.mapper;

import com.petlee.dto.AdminPetDTO;
import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.model.Pet;
import com.petlee.model.User;

/**
 * {@link Pet} to its two wire shapes.
 *
 * <p>These methods read an already-fetched graph and nothing else: no {@code EntityManager}, no
 * lazy loading. T-08's queries fetch the category and owner they need, so if something is missing
 * here the fix belongs in the repository query.
 */
public final class PetMapper {

    private PetMapper() {
    }

    /**
     * The gallery shape: main image only, no owner contact details.
     *
     * @param pet the pet, may be {@code null}
     * @return the DTO, or {@code null} for a {@code null} argument. {@code mainImageUrl} is
     *         {@code null} when the pet has no photograph.
     */
    public static PetDTO toDto(Pet pet) {
        if (pet == null) {
            return null;
        }
        PetDTO dto = new PetDTO();
        dto.setId(pet.getPetId());
        dto.setName(pet.getPetName());
        dto.setShortDesc(pet.getShortDesc());
        dto.setAge(pet.getAge());
        dto.setSize(name(pet.getSize()));
        dto.setGender(name(pet.getGender()));
        dto.setStatus(name(pet.getStatus()));
        dto.setCategoryName(pet.getCategory() == null ? null : pet.getCategory().getCategoryName());
        dto.setMainImageUrl(pet.getImageUrl());
        return dto;
    }

    /**
     * The moderation shape: everything the gallery shows, plus the owner's name and the date the
     * listing was created. T-34's {@code GET /api/admin/pets} is its only caller.
     *
     * @param pet the pet, may be {@code null}
     * @return the DTO, or {@code null} for a {@code null} argument
     */
    public static AdminPetDTO toAdminDto(Pet pet) {
        if (pet == null) {
            return null;
        }
        AdminPetDTO dto = new AdminPetDTO();
        dto.setId(pet.getPetId());
        dto.setName(pet.getPetName());
        dto.setShortDesc(pet.getShortDesc());
        dto.setAge(pet.getAge());
        dto.setSize(name(pet.getSize()));
        dto.setGender(name(pet.getGender()));
        dto.setStatus(name(pet.getStatus()));
        dto.setCategoryName(pet.getCategory() == null ? null : pet.getCategory().getCategoryName());
        dto.setMainImageUrl(pet.getImageUrl());
        dto.setOwnerName(pet.getOwner() == null ? null : pet.getOwner().getFullName());
        dto.setCreatedAt(pet.getCreatedAt() == null ? null : pet.getCreatedAt().toString());
        return dto;
    }

    /**
     * The details shape: every field, every image, and the owner's contact details when the caller
     * is allowed to see them.
     *
     * @param pet            the pet, may be {@code null}
     * @param includeContact {@code true} only for a logged-in caller. When {@code false},
     *                       {@code ownerFullName}, {@code ownerEmail} and {@code ownerPhone} are
     *                       left {@code null} — specification §6 and the contract both require
     *                       that a guest cannot see them. The parameter has no default so that no
     *                       caller can forget to decide.
     * @return the DTO, or {@code null} for a {@code null} argument
     */
    public static PetDetailDTO toDetailDto(Pet pet, boolean includeContact) {
        if (pet == null) {
            return null;
        }
        PetDetailDTO dto = new PetDetailDTO();
        dto.setId(pet.getPetId());
        dto.setName(pet.getPetName());
        dto.setBreed(pet.getBreed());
        dto.setAge(pet.getAge());
        dto.setSize(name(pet.getSize()));
        dto.setGender(name(pet.getGender()));
        dto.setShortDesc(pet.getShortDesc());
        dto.setLongDesc(pet.getLongDesc());
        dto.setStatus(name(pet.getStatus()));
        dto.setCategoryName(pet.getCategory() == null ? null : pet.getCategory().getCategoryName());
        dto.setImageUrl(pet.getImageUrl());

        User owner = pet.getOwner();
        if (includeContact && owner != null) {
            dto.setOwnerFullName(owner.getFullName());
            dto.setOwnerEmail(owner.getEmail());
            dto.setOwnerPhone(owner.getPhoneNumber());
        }
        return dto;
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
