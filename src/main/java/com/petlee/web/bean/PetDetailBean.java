package com.petlee.web.bean;

import com.petlee.model.Pet;
import com.petlee.service.AppException;
import com.petlee.service.PetService;

import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serializable;

/**
 * Backs {@code petDetails.xhtml}: one listing, loaded once by a view action.
 * Whether the owner's contact details are shown is gated here, by {@link #isContactVisible()}.
 */
@Named("petDetailBean")
@ViewScoped
public class PetDetailBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject private transient PetService petService;

    /** Not transient: see UserBean's own field for why. */
    @Inject private UserBean userBean;

    private Long petId;
    private Pet pet;

    /**
     * Fetches the listing. Bound as an {@code <f:viewAction>}, so it runs once before rendering.
     *
     * @return null to render this page; the response is already complete on a 404
     */
    public String load() {
        if (petId == null) {
            return notFound();
        }
        try {
            pet = petService.findDetail(petId);
            return null;

        } catch (AppException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
                return notFound();
            }
            Messages.error(failure.getMessage());
            return null;
        }
    }

    /**
     * Answers 404 directly. A navigation outcome would redirect, sending 302 then 200 for a page
     * that says "not found".
     *
     * @return null — the response is already complete
     */
    private String notFound() {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            context.getExternalContext().responseSendError(HttpServletResponse.SC_NOT_FOUND, null);
        } catch (IOException connectionGone) {
            // The client hung up mid-response. Nothing useful can be sent.
        }
        context.responseComplete();
        return null;
    }

    /** @return the listing's photograph, or the bundled placeholder when it has none */
    public String getImageUrl() { return PetBean.imageOf(pet); }

    /** @return whether the listing is no longer available */
    public boolean isWithdrawn() {
        return pet != null && pet.getStatus() != Pet.PetStatus.AVAILABLE;
    }

    /**
     * The tier's own privacy gate: the entity always carries its owner, so the page must not.
     *
     * @return whether the owner's contact details may be shown
     */
    public boolean isContactVisible() {
        return userBean.isLoggedIn();
    }

    public Long getPetId() { return petId; }

    public void setPetId(Long petId) { this.petId = petId; }

    public Pet getPet() { return pet; }
}
