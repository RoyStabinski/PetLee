package com.petlee.web.bean;

import com.petlee.dto.PetDTO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of {@link PetManagedBean} that do not need a {@code FacesContext}.
 *
 * <p>Loading, filtering and error reporting all need a request, and are demonstrated against the
 * running application in T-28's acceptance criteria. What is testable here is the placeholder rule
 * and the shape of the detail-page outcome — both small, and both the kind of thing that fails
 * silently: a broken image icon looks like a missing photograph, and a malformed outcome looks like
 * a dead link.
 */
class PetManagedBeanTest {

    private final PetManagedBean bean = new PetManagedBean();

    @Test
    @DisplayName("criterion 8 — a pet with no photograph gets the bundled placeholder")
    void placeholderForAPetWithNoImage() {
        assertEquals(PetManagedBean.PLACEHOLDER_IMAGE, bean.getMainImageUrl(new PetDTO()));
    }

    /**
     * A blank string is what an empty column deserialises to, and it renders exactly as badly as
     * {@code null}: {@code <img src="">} reloads the current page as the image.
     */
    @ParameterizedTest
    @DisplayName("and so does one whose url is blank")
    @ValueSource(strings = {"", "   "})
    void placeholderForABlankUrl(String blank) {
        PetDTO pet = new PetDTO();
        pet.setMainImageUrl(blank);

        assertEquals(PetManagedBean.PLACEHOLDER_IMAGE, bean.getMainImageUrl(pet));
    }

    @Test
    @DisplayName("a null pet never produces a null src")
    void placeholderForNoPetAtAll() {
        assertEquals(PetManagedBean.PLACEHOLDER_IMAGE, bean.getMainImageUrl(null));
    }

    @Test
    @DisplayName("a real photograph is passed through untouched")
    void realImageIsUsed() {
        PetDTO pet = new PetDTO();
        pet.setMainImageUrl("/images/185_12151c2b.jpg");

        assertEquals("/images/185_12151c2b.jpg", bean.getMainImageUrl(pet));
    }

    /**
     * {@code includeViewParams} is what carries {@code id} through the redirect. Without it the
     * detail page arrives with no pet to show, and the link cannot be bookmarked or shared.
     */
    @Test
    @DisplayName("the detail outcome redirects and carries the id")
    void detailOutcome() {
        String outcome = bean.viewDetails(185L);

        assertEquals("/petDetails.xhtml?faces-redirect=true&includeViewParams=true&id=185", outcome);
        assertTrue(outcome.contains("faces-redirect=true"), "must redirect, so the URL is shareable");
    }

    @Test
    @DisplayName("an unloaded gallery reports itself empty rather than throwing")
    void emptyBeforeAnythingIsLoaded() {
        assertTrue(bean.isEmpty());
        assertTrue(bean.getPets().isEmpty());
        assertTrue(bean.getCategories().isEmpty());
    }
}
