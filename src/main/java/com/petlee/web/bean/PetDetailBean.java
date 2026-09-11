package com.petlee.web.bean;

import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetImageDTO;
import com.petlee.exception.NotFoundException;
import com.petlee.exception.PetLeeException;
import com.petlee.service.PetService;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * One listing in full — {@code #{petDetailBean}}.
 *
 * <h2>Loaded once, by a view action</h2>
 * The id arrives as a view parameter and {@link #load()} runs as an {@code <f:viewAction>}.
 * Fetching in a getter instead would call the service once per EL evaluation on the page.
 *
 * <h2>Contact details are the server's decision</h2>
 * This bean never redacts anything. {@link PetService#findDetail(Long, Long)} returns
 * {@code ownerFullName}, {@code ownerEmail} and {@code ownerPhone} as {@code null} for a guest
 * caller — specification §6's privacy rule. The page's {@code rendered} check is a second layer on
 * top of that, not a substitute for it.
 */
@Named("petDetailBean")
@ViewScoped
public class PetDetailBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject private transient PetService petService;

    /** Not {@code transient}: see {@link UserBean}'s own field for why. */
    @Inject private UserBean userBean;

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
            pet = petService.findDetail(petId, userBean.getCurrentUserId());
            selectedImage = 0;
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

    /**
     * Puts a thumbnail in the large frame.
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

    /** @return every photograph; never {@code null} */
    public List<PetImageDTO> getImages() {
        return images();
    }

    /**
     * @return whether there is more than one photograph. A single-image listing renders no
     *         thumbnail strip at all.
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
     *         <p><strong>This is the second layer, not the only one.</strong> The service has
     *         already replaced these fields with {@code null} for a caller with no session.
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
