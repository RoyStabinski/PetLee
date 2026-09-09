package com.petlee.mapper;

import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetImageDTO;
import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.PetImage;
import com.petlee.model.User;
import com.petlee.test.TestData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PetMapper}, and above all the flag behind specification §6.
 *
 * <p><em>"Unregistered clients can view details of pets offered for adoption without seeing private
 * contact information."</em> {@code toDetailDto}'s {@code includeContact} argument is the mechanism
 * that enforces it, so it is tested exhaustively: both values, every field, and the difference
 * between the two results named exactly.
 */
class PetMapperTest {

    private User owner;
    private Pet pet;

    @BeforeEach
    void setUp() {
        owner = TestData.aUser()
                .fullName("Donald Trump")
                .email("djt@usa.com")
                .phone("050-1234567")
                .build();
        owner.setUserId(1L);

        Category dogs = TestData.aCategory().name("Dogs").build();
        dogs.setCategoryId(1);

        pet = TestData.aPet().owner(owner).category(dogs).name("Rex").build();
        pet.setPetId(10L);

        PetImage main = TestData.aPetImage().pet(pet).url("/images/rex-main.jpg").main(true).build();
        main.setImageId(101);
        PetImage second = TestData.aPetImage().pet(pet).url("/images/rex-3.jpg").main(false).build();
        second.setImageId(102);
    }

    @Test
    @DisplayName("specification §6: a guest's detail view has all three owner fields null")
    void toDetailDto_whenCallerIsGuest_masksEveryContactField() {
        PetDetailDTO guestView = PetMapper.toDetailDto(pet, false);

        assertNull(guestView.getOwnerFullName());
        assertNull(guestView.getOwnerEmail());
        assertNull(guestView.getOwnerPhone());
    }

    @Test
    @DisplayName("contract: \"filled ONLY if the caller is logged in\" — so a member sees all three")
    void toDetailDto_whenCallerIsLoggedIn_populatesEveryContactField() {
        PetDetailDTO memberView = PetMapper.toDetailDto(pet, true);

        assertEquals("Donald Trump", memberView.getOwnerFullName());
        assertEquals("djt@usa.com", memberView.getOwnerEmail());
        assertEquals("050-1234567", memberView.getOwnerPhone());
    }

    /**
     * The half of the rule that is easy to break silently: hiding a contact detail must not also
     * hide the listing. If a later change masks anything else, this fails and names the field.
     */
    @Test
    @DisplayName("specification §6: and nothing else differs between the two views")
    void toDetailDto_whenMasking_changesOnlyTheThreeContactFields() throws ReflectiveOperationException {
        PetDetailDTO guestView = PetMapper.toDetailDto(pet, false);
        PetDetailDTO memberView = PetMapper.toDetailDto(pet, true);

        List<String> contactFields = List.of("ownerFullName", "ownerEmail", "ownerPhone");
        for (Field field : PetDetailDTO.class.getDeclaredFields()) {
            if (field.isSynthetic() || contactFields.contains(field.getName())) {
                continue;
            }
            field.setAccessible(true);
            // Rendered rather than compared directly: the DTOs are two separate object graphs, and
            // PetImageDTO has no equals(), so identity would fail on a list that is in fact equal.
            assertEquals(render(field.get(memberView)), render(field.get(guestView)),
                    field.getName() + " must not depend on whether the caller is logged in");
        }
    }

    /** A comparable rendering of any field value, including the image list. */
    private static String render(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> item instanceof PetImageDTO image
                            ? image.getId() + ":" + image.getImageUrl() + ":" + image.getIsMain()
                            : String.valueOf(item))
                    .toList()
                    .toString();
        }
        return String.valueOf(value);
    }

    @Test
    @DisplayName("the gallery shape carries the main image and no owner field at all")
    void toDto_takesTheMainImageAndNeverAnOwner() {
        PetDTO gallery = PetMapper.toDto(pet);

        assertEquals("/images/rex-main.jpg", gallery.getMainImageUrl());
        assertEquals("Dogs", gallery.getCategoryName());
        assertTrue(java.util.Arrays.stream(PetDTO.class.getDeclaredFields())
                        .noneMatch(f -> f.getName().toLowerCase().contains("owner")),
                "PetDTO is the public gallery body: an owner field here would be a §6 leak");
    }

    @Test
    @DisplayName("a pet with no image flagged main has a null thumbnail, not the first photograph")
    void toDto_whenNoMainImage_leavesTheThumbnailNull() {
        pet.getImages().forEach(image -> image.setIsMain(false));

        assertNull(PetMapper.toDto(pet).getMainImageUrl());
    }

    @Test
    @DisplayName("the details view lists every photograph, main first or not")
    void toDetailDto_carriesEveryImage() {
        PetDetailDTO view = PetMapper.toDetailDto(pet, true);

        assertEquals(2, view.getImages().size());
        assertNotNull(view.getImages().get(0).getImageUrl());
    }

    @Test
    @DisplayName("a null pet maps to null rather than an empty DTO nobody can tell apart")
    void mapsNullToNull() {
        assertNull(PetMapper.toDto(null));
        assertNull(PetMapper.toDetailDto(null, true));
        assertNull(PetMapper.toAdminDto(null));
        assertNull(PetMapper.toImageDto(null));
    }
}
