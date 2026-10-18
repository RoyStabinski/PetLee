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
import java.util.ArrayList;
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

    // Not transient: what CDI injects is the client proxy of a normal-scoped bean, which is
    // serializable; javac sees only the declared type. Transient would leave null after
    // the session or view is passivated and restored.
    @SuppressWarnings("serial") @Inject private PetApi petApi;

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

    /**
     * @return the photographs for the details page: the main one first and the rest oldest
     *         first; just the placeholder when the listing has none
     */
    public List<PetImageDTO> getPhotos() {
        if (pet == null || pet.images() == null || pet.images().isEmpty()) {
            return List.of(new PetImageDTO(null, getImageUrl(), true));
        }
        List<PetImageDTO> photos = new ArrayList<>();
        pet.images().stream().filter(PetImageDTO::main).forEach(photos::add);
        pet.images().stream().filter(image -> !image.main()).forEach(photos::add);
        return photos;
    }

    /** @return how many photographs the details page shows; at least one, the placeholder */
    public int getPhotoCount() { return getPhotos().size(); }

    /** @return the age as the gallery shows it, or "Unknown" */
    public String getAgeLabel() {
        String label = PetBean.ageLabelOf(pet == null ? null : pet.age());
        return label.isEmpty() ? "Unknown" : label;
    }

    /** @return the CSS classes for the listing's status badge */
    public String getStatusStyle() { return PetBean.statusClassOf(pet == null ? null : pet.status()); }

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

    /** @return the listing's id, from the {@code id} view parameter */
    public Long getPetId() { return petId; }

    /** @param petId the listing's id, from the {@code id} view parameter */
    public void setPetId(Long petId) { this.petId = petId; }

    /** @return the listing as the API returned it, or null before it loads or on a failure */
    public PetDetailDTO getPet() { return pet; }
}
