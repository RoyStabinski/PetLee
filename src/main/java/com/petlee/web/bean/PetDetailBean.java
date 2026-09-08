package com.petlee.web.bean;

import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetImageDTO;
import com.petlee.web.client.ApiClient;
import com.petlee.web.client.ApiException;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One listing in full — {@code #{petDetailBean}}.
 *
 * <h2>Loaded once, by a view action</h2>
 * The id arrives as a view parameter and {@link #load()} runs as an {@code <f:viewAction>}.
 * Fetching in a getter instead would be a fresh API call for every EL evaluation on the page —
 * a dozen HTTP round trips to render one pet, and the number would grow with the markup.
 *
 * <h2>Contact details are the server's decision</h2>
 * This bean never redacts anything. {@code GET /api/pets/{id}} returns {@code ownerFullName},
 * {@code ownerEmail} and {@code ownerPhone} as {@code null} for a caller with no session (T-15,
 * T-22), which is the contract's {@code open*} footnote and specification §6's privacy rule. The
 * page's {@code rendered} check is a second layer on top of that, not a substitute for it.
 */
@Named("petDetailBean")
@ViewScoped
public class PetDetailBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(PetDetailBean.class.getName());

    @Inject
    private ApiClient api;

    private Long petId;
    private PetDetailDTO pet;

    /** Which photograph the large frame is showing. Zero is the main one. */
    private int selectedImage;

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
            pet = api.getPet(petId);
            selectedImage = 0;
            return null;

        } catch (ApiException failure) {
            if (failure.isNotFound()) {
                LOGGER.log(Level.FINE, () -> "no listing " + petId);
                return notFound();
            }
            // Anything else - the API unreachable, a 500 - is worth telling the user about on a
            // page they can read, rather than turning into a not-found that would send them
            // looking for a listing that does exist.
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, failure.getMessage(), null));
            return null;
        }
    }

    /**
     * Answers 404, and lets the container put T-33's not-found page in the body.
     *
     * <p>Not a navigation outcome. An {@code <f:viewAction>} that returns one is redirected by
     * Faces, so the browser would be sent to {@code /error/404.xhtml} and receive <strong>302 then
     * 200</strong> — a page that says "not found" over a response that says everything is fine.
     * Crawlers, monitoring and T-39's tests all read the status, not the prose.
     *
     * <p>{@code responseSendError} hands the request to the container's error machinery, which
     * web.xml already maps to the same page, with the status intact and the URL unchanged.
     *
     * @return {@code null}: the response is already complete
     */
    private String notFound() {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            context.getExternalContext().responseSendError(HttpServletResponse.SC_NOT_FOUND, null);
        } catch (IOException connectionGone) {
            // The client hung up mid-response. Nothing useful can be sent and nothing is broken.
            LOGGER.log(Level.FINE, "could not send 404", connectionGone);
        }
        context.responseComplete();
        return null;
    }

    /**
     * Puts a thumbnail in the large frame.
     *
     * <p>Plain JSF: an AJAX action that re-renders the frame. No image-gallery library, nothing to
     * keep up to date, and it works with JavaScript disabled as a full postback.
     *
     * @param index the photograph to show
     */
    public void select(int index) {
        if (pet != null && index >= 0 && index < images().size()) {
            selectedImage = index;
        }
    }

    /** @return the photograph currently in the large frame, or {@code null} when there are none */
    public PetImageDTO getSelectedImage() {
        List<PetImageDTO> images = images();
        return images.isEmpty() ? null : images.get(Math.min(selectedImage, images.size() - 1));
    }

    public int getSelectedImageIndex() {
        return selectedImage;
    }

    /** @return every photograph, newest ordering as the API returned it; never {@code null} */
    public List<PetImageDTO> getImages() {
        return images();
    }

    /**
     * @return whether there is more than one photograph. A single-image listing renders no
     *         thumbnail strip at all — a strip of one is a row of nothing useful.
     */
    public boolean isHasThumbnails() {
        return images().size() > 1;
    }

    /** @return whether the listing is no longer available, so the page can say so plainly */
    public boolean isWithdrawn() {
        return pet != null && !"AVAILABLE".equals(pet.getStatus());
    }

    /**
     * @return whether the owner's contact details are present.
     *         <p><strong>This is the second layer, not the only one.</strong> The server has
     *         already replaced these fields with {@code null} for a caller with no session, so a
     *         guest's response never contains them. Removing either check leaves the other doing
     *         the whole job, and the one that matters is the server's.
     */
    public boolean isContactAvailable() {
        return pet != null && pet.getOwnerEmail() != null;
    }

    private List<PetImageDTO> images() {
        return pet == null || pet.getImages() == null ? List.of() : pet.getImages();
    }

    public Long getPetId() {
        return petId;
    }

    public void setPetId(Long petId) {
        this.petId = petId;
    }

    public PetDetailDTO getPet() {
        return pet;
    }
}
