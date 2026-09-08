package com.petlee.web.bean;

import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetImageDTO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decisions {@link PetDetailBean} makes about what to draw.
 *
 * <p>Loading and the 404 path need a {@code FacesContext} and are demonstrated against the running
 * application. These are the ones that would fail quietly: a thumbnail strip of one, an index that
 * outran its list after a reload, or a contact block rendered empty because the page asked the
 * wrong question.
 */
class PetDetailBeanTest {

    @Test
    @DisplayName("before anything is loaded, nothing throws and nothing is drawn")
    void unloaded() {
        PetDetailBean bean = new PetDetailBean();

        assertNull(bean.getPet());
        assertNull(bean.getSelectedImage());
        assertTrue(bean.getImages().isEmpty());
        assertFalse(bean.isHasThumbnails());
        assertFalse(bean.isWithdrawn());
        assertFalse(bean.isContactAvailable());
    }

    @Test
    @DisplayName("criterion 5 — one photograph draws no thumbnail strip")
    void singleImageHasNoStrip() {
        PetDetailBean bean = beanFor(pet("AVAILABLE", null, image("/images/a.jpg")));

        assertFalse(bean.isHasThumbnails());
        assertEquals("/images/a.jpg", bean.getSelectedImage().getImageUrl());
    }

    @Test
    @DisplayName("two photographs do")
    void twoImagesGetAStrip() {
        PetDetailBean bean = beanFor(pet("AVAILABLE", null, image("/images/a.jpg"), image("/images/b.jpg")));

        assertTrue(bean.isHasThumbnails());
    }

    @Test
    @DisplayName("a listing with no photographs falls back rather than failing")
    void noImages() {
        PetDetailBean bean = beanFor(pet("AVAILABLE", null));

        assertNull(bean.getSelectedImage());
        assertFalse(bean.isHasThumbnails());
    }

    @Test
    @DisplayName("selecting a thumbnail moves the large frame")
    void selectingAThumbnail() {
        PetDetailBean bean = beanFor(pet("AVAILABLE", null, image("/images/a.jpg"), image("/images/b.jpg")));

        bean.select(1);

        assertEquals("/images/b.jpg", bean.getSelectedImage().getImageUrl());
    }

    /** A stale index from a re-rendered view must not throw; it clamps to the last photograph. */
    @Test
    @DisplayName("an out-of-range selection is ignored")
    void outOfRangeSelection() {
        PetDetailBean bean = beanFor(pet("AVAILABLE", null, image("/images/a.jpg")));

        bean.select(9);
        bean.select(-1);

        assertEquals("/images/a.jpg", bean.getSelectedImage().getImageUrl());
        assertEquals(0, bean.getSelectedImageIndex());
    }

    @Test
    @DisplayName("an adopted or withdrawn listing says so")
    void withdrawnStatuses() {
        assertFalse(beanFor(pet("AVAILABLE", null)).isWithdrawn());
        assertTrue(beanFor(pet("ADOPTED", null)).isWithdrawn());
        assertTrue(beanFor(pet("REMOVED", null)).isWithdrawn());
    }

    /**
     * The second layer. The server has already nulled these fields for a guest; this only decides
     * whether to draw a block, and it must decide "no" rather than draw an empty one.
     */
    @Test
    @DisplayName("the contact block follows what the server sent, not what the page hoped for")
    void contactBlockFollowsTheServer() {
        assertFalse(beanFor(pet("AVAILABLE", null)).isContactAvailable());
        assertTrue(beanFor(pet("AVAILABLE", "owner@example.org")).isContactAvailable());
    }

    @Test
    @DisplayName("the images list is the one the API returned, in its order")
    void imagesArePassedThroughUntouched() {
        PetImageDTO first = image("/images/a.jpg");
        PetImageDTO second = image("/images/b.jpg");
        PetDetailBean bean = beanFor(pet("AVAILABLE", null, first, second));

        List<PetImageDTO> images = bean.getImages();
        assertEquals(2, images.size());
        assertSame(first, images.get(0));
        assertSame(second, images.get(1));
    }

    // ------------------------------------------------------------------------------ scaffolding

    private static PetDetailBean beanFor(PetDetailDTO pet) {
        PetDetailBean bean = new PetDetailBean();
        set(bean, "pet", pet);
        return bean;
    }

    /**
     * The bean has no setter for {@code pet} on purpose — only {@code load()} may fill it, and
     * {@code load()} needs a container. Reflection is the honest way to reach the state a real
     * request would have produced, rather than adding a setter that exists for testing alone.
     */
    private static void set(PetDetailBean bean, String field, Object value) {
        try {
            Field target = PetDetailBean.class.getDeclaredField(field);
            target.setAccessible(true);
            target.set(bean, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("PetDetailBean." + field + " has moved", e);
        }
    }

    private static PetDetailDTO pet(String status, String ownerEmail, PetImageDTO... images) {
        PetDetailDTO pet = new PetDetailDTO();
        pet.setName("Rex");
        pet.setStatus(status);
        pet.setOwnerEmail(ownerEmail);
        pet.setImages(new ArrayList<>(List.of(images)));
        return pet;
    }

    private static PetImageDTO image(String url) {
        PetImageDTO image = new PetImageDTO();
        image.setImageUrl(url);
        return image;
    }
}
