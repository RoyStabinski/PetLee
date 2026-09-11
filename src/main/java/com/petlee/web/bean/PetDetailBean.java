package com.petlee.web.bean;

import com.petlee.exception.NotFoundException;
import com.petlee.exception.PetLeeException;
import com.petlee.model.Pet;
import com.petlee.service.PetService;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serializable;

/**
 * One listing in full — {@code #{petDetailBean}}.
 *
 * <h2>Loaded once, by a view action</h2>
 * The id arrives as a view parameter and {@link #load()} runs as an {@code <f:viewAction>}.
 * Fetching in a getter instead would call the service once per EL evaluation on the page.
 *
 * <h2>Contact details are gated here, not by the service</h2>
 * {@link PetService#findDetail(Long)} now returns the full entity, owner attached,
 * unconditionally — it has to, because {@code Pet} has one shape and cannot answer differently for
 * a guest and a member. {@link #isContactVisible()} is this tier's own copy of specification §6's
 * rule, delegating to {@link UserBean#isLoggedIn()}; {@code petDetails.xhtml} wraps the contact
 * block in {@code rendered="#{petDetailBean.contactVisible}"}. The REST side makes the identical
 * decision independently, in {@code PetDetailDTO.of(Pet, boolean)} — the two cannot share one
 * method because a record and an entity are different types, so both must gate or a guest on one
 * tier sees what a guest on the other cannot.
 */
@Named("petDetailBean")
@ViewScoped
public class PetDetailBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject private transient PetService petService;

    /** Not {@code transient}: see {@link UserBean}'s own field for why. */
    @Inject private UserBean userBean;

    private Long petId;
    private Pet pet;

    /**
     * Fetches the listing, or sends the visitor to the not-found page.
     *
     * <p>Bound as an {@code <f:viewAction>}, so it runs once per view, before rendering, and its
     * return value is a navigation outcome.
     *
     * @return {@code null} to render this page, or the not-found outcome
     */
    public String load() {
        if (petId == null) {
            // A bookmark that lost its query string, or a hand-typed URL. There is no listing to
            // show and no useful page to render, so this is a 404 like any other.
            return notFound();
        }
        try {
            pet = petService.findDetail(petId);
            return null;

        } catch (NotFoundException noSuchPet) {
            return notFound();
        } catch (PetLeeException failure) {
            // Anything else is worth telling the user about on a page they can read, rather than
            // turning into a not-found that would send them looking for a listing that does exist.
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, failure.getMessage(), null));
            return null;
        }
    }

    /**
     * Answers 404, and lets the container put the not-found page in the body.
     *
     * <p>Not a navigation outcome. An {@code <f:viewAction>} that returns one is redirected by
     * Faces, so the browser would be sent to {@code /error/404.xhtml} and receive <strong>302 then
     * 200</strong> — a page that says "not found" over a response that says everything is fine.
     *
     * @return {@code null}: the response is already complete
     */
    private String notFound() {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            context.getExternalContext().responseSendError(HttpServletResponse.SC_NOT_FOUND, null);
        } catch (IOException connectionGone) {
            // The client hung up mid-response. Nothing useful can be sent and nothing is broken.
        }
        context.responseComplete();
        return null;
    }

    /** @return the listing's photograph, or the bundled placeholder when it has none */
    public String getImageUrl() {
        String url = pet == null ? null : pet.getImageUrl();
        return url == null || url.isBlank() ? PetBean.PLACEHOLDER_IMAGE : url;
    }

    /** @return whether the listing is no longer available, so the page can say so plainly */
    public boolean isWithdrawn() {
        return pet != null && pet.getStatus() != Pet.PetStatus.AVAILABLE;
    }

    /**
     * @return whether the owner's contact details may be shown.
     *         <p><strong>This is the JSF tier's own gate, not a convenience.</strong> The entity
     *         {@link #getPet()} exposes always carries its owner; nothing about the object itself
     *         says whether the current visitor may see the owner's phone number and email address.
     *         Specification §6 is enforced here, by delegating to {@link UserBean#isLoggedIn()}.
     */
    public boolean isContactVisible() {
        return userBean.isLoggedIn();
    }

    public Long getPetId() {
        return petId;
    }

    public void setPetId(Long petId) {
        this.petId = petId;
    }

    public Pet getPet() {
        return pet;
    }
}
