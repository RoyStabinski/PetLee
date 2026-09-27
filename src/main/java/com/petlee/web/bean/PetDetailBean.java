package com.petlee.web.bean;

import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetImageDTO;
import com.petlee.web.client.ApiException;
import com.petlee.web.client.PetApi;

import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * Backs {@code petDetails.xhtml}: one listing, loaded once by a view action through the REST API.
 * The API fills the owner's contact details only for a logged-in caller, so the server is the
 * privacy gate; {@link #isContactVisible()} just follows what it sent.
 */
@Named("petDetailBean")
@ViewScoped
public class PetDetailBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Not transient: see UserBean's own field for why. */
    @Inject private PetApi petApi;

    private Long petId;
    private PetDetailDTO pet;

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
            pet = petApi.detail(petId);
            return null;

        } catch (ApiException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
                return notFound();
            }
            if (failure.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                return Messages.sessionExpired();
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
    public String getImageUrl() { return PetBean.imageOf(pet == null ? null : pet.imageUrl()); }

    /** @return the photographs other than the main one, oldest first, for the row beneath it */
    public List<PetImageDTO> getOtherImages() {
        if (pet == null || pet.images() == null) {
            return List.of();
        }
        return pet.images().stream().filter(image -> !image.main()).toList();
    }

    /** @return whether the listing is no longer available */
    public boolean isWithdrawn() {
        return pet != null && !"AVAILABLE".equals(pet.status());
    }

    /**
     * Follows the server's privacy decision: {@code ownerEmail} is required on every account, so
     * it is null exactly when the API did not see a logged-in caller.
     *
     * @return whether the API sent the owner's contact details
     */
    public boolean isContactVisible() {
        return pet != null && pet.ownerEmail() != null;
    }

    public Long getPetId() { return petId; }

    public void setPetId(Long petId) { this.petId = petId; }

    public PetDetailDTO getPet() { return pet; }
}
