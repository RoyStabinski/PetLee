package com.petlee.web.bean;

import com.petlee.dto.AdminPetDTO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of {@link AdminBean} that do not need a {@code FacesContext}.
 *
 * <p>Loading, moderating and the messages that follow all need a request, and are demonstrated
 * against the running application in T-35's acceptance criteria. What is worth pinning here is the
 * date the moderation table prints — the wire carries nanoseconds, and a column that wraps or shows
 * {@code 2026-09-08T13:44:01.929590300} is a defect nobody writes a test for afterwards.
 */
class AdminBeanTest {

    private final AdminBean bean = new AdminBean();

    @Test
    @DisplayName("the ISO timestamp becomes a date and a time, to the minute")
    void formatsTheCreationDate() {
        assertEquals("2026-09-08 13:44", bean.createdOn(pet("2026-09-08T13:44:01.929590300")));
        assertEquals("2026-09-08 13:44", bean.createdOn(pet("2026-09-08T13:44:01")));
    }

    @Test
    @DisplayName("a missing or unexpected timestamp never renders as null")
    void survivesAnUnexpectedTimestamp() {
        assertEquals("", bean.createdOn(pet(null)));
        assertEquals("", bean.createdOn(null));
        assertEquals("2026-09-08", bean.createdOn(pet("2026-09-08")), "too short to split, shown as-is");
    }

    @Test
    @DisplayName("REMOVED is the one status that offers Restore instead of Hide")
    void recognisesAHiddenListing() {
        assertTrue(bean.isHidden(pet("REMOVED", "x")));
        assertFalse(bean.isHidden(pet("AVAILABLE", "x")));
        assertFalse(bean.isHidden(pet("ADOPTED", "x")));
        assertFalse(bean.isHidden(null));
    }

    @Test
    @DisplayName("a listing with no photograph gets the bundled placeholder, never a broken image")
    void placeholderForAPetWithNoImage() {
        assertEquals(PetManagedBean.PLACEHOLDER_IMAGE, bean.thumbnailOf(new AdminPetDTO()));
        assertEquals(PetManagedBean.PLACEHOLDER_IMAGE, bean.thumbnailOf(null));

        AdminPetDTO photographed = new AdminPetDTO();
        photographed.setMainImageUrl("/images/185_12151c2b.jpg");
        assertEquals("/images/185_12151c2b.jpg", bean.thumbnailOf(photographed));
    }

    @Test
    @DisplayName("the status badge is coloured by status, and an absent one still has a class")
    void colloursTheStatusBadge() {
        assertEquals("tag tag-removed", bean.statusStyle(pet("REMOVED", "x")));
        assertEquals("tag tag-adopted", bean.statusStyle(pet("ADOPTED", "x")));
        assertEquals("tag", bean.statusStyle(pet("AVAILABLE", "x")));
        assertEquals("tag", bean.statusStyle(new AdminPetDTO()));
    }

    private static AdminPetDTO pet(String createdAt) {
        AdminPetDTO pet = new AdminPetDTO();
        pet.setCreatedAt(createdAt);
        return pet;
    }

    private static AdminPetDTO pet(String status, String name) {
        AdminPetDTO pet = new AdminPetDTO();
        pet.setStatus(status);
        pet.setName(name);
        return pet;
    }
}
