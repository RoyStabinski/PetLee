package com.petlee.mapper;

import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetImageDTO;
import com.petlee.model.Pet;
import com.petlee.model.PetImage;
import com.petlee.model.User;

import java.util.List;

/**
 * {@link Pet} to its two wire shapes.
 *
 * <p>These methods read an already-fetched graph and nothing else: no {@code EntityManager}, no
 * lazy loading. T-08's queries fetch the category, owner and images they need, so if something is
 * missing here the fix belongs in the repository query.
 */
public final class PetMapper {

    private PetMapper() {
    }

    /**
     * The gallery shape: main image only, no owner contact details.
     *
     * @param pet the pet, may be {@code null}
     * @return the DTO, or {@code null} for a {@code null} argument. {@code mainImageUrl} is
     *         {@code null} when the pet has no image flagged main.
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
        dto.setMainImageUrl(mainImageUrl(pet));
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
        dto.setImages(toImageDtos(pet.getImages()));

        User owner = pet.getOwner();
        if (includeContact && owner != null) {
            dto.setOwnerFullName(owner.getFullName());
            dto.setOwnerEmail(owner.getEmail());
            dto.setOwnerPhone(owner.getPhoneNumber());
        }
        return dto;
    }

    /**
     * @param image the image, may be {@code null}
     * @return the DTO, or {@code null} for a {@code null} argument
     */
    public static PetImageDTO toImageDto(PetImage image) {
        if (image == null) {
            return null;
        }
        PetImageDTO dto = new PetImageDTO();
        dto.setId(image.getImageId());
        dto.setImageUrl(image.getImageUrl());
        dto.setIsMain(Boolean.TRUE.equals(image.getIsMain()));
        return dto;
    }

    private static List<PetImageDTO> toImageDtos(List<PetImage> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream().map(PetMapper::toImageDto).toList();
    }

    private static String mainImageUrl(Pet pet) {
        if (pet.getImages() == null) {
            return null;
        }
        return pet.getImages().stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsMain()))
                .map(PetImage::getImageUrl)
                .findFirst()
                .orElse(null);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
