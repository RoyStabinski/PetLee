package com.petlee.web.bean;

import com.petlee.dto.PetDTO;
import com.petlee.web.client.ApiClient;
import com.petlee.web.client.ApiException;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The owner's dashboard — {@code #{myListingsBean}}.
 *
 * <h2>Why this is not the gallery</h2>
 * It shows <strong>every</strong> status, {@code ADOPTED} and {@code REMOVED} included. An owner
 * who withdrew a listing has to be able to see that they did; the public gallery deliberately hides
 * exactly those, so a dashboard built on {@code GET /api/pets} would silently lose the listings its
 * user most needs. That is what {@code GET /api/pets/mine} exists for (ADR-002 #11).
 *
 * <p>It is a table and not the card grid, because the two answer different questions. A grid is for
 * browsing; a table is for finding one row and doing something to it.
 */
@Named("myListingsBean")
@ViewScoped
public class MyListingsBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(MyListingsBean.class.getName());

    private static final String LOGIN = "/login.xhtml?faces-redirect=true";

    @Inject
    private ApiClient api;

    private List<PetDTO> listings = Collections.emptyList();

    @PostConstruct
    void init() {
        load();
    }

    private void load() {
        try {
            listings = api.getMyPets();
            LOGGER.log(Level.FINE, () -> "dashboard loaded: " + listings.size() + " listings");
        } catch (ApiException failure) {
            error(failure.getMessage());
        }
    }

    /**
     * Removes a listing and refreshes the table.
     *
     * <p>The page asks for confirmation before this runs. Deletion is permanent and takes the
     * photographs with it (T-03's cascade), so a mis-click must not be able to reach here.
     *
     * @param petId the listing to remove
     * @return {@code null} to stay on the dashboard, or the login page if the session has gone
     */
    public String delete(Long petId) {
        try {
            api.deletePet(petId);
            info(message("profile.deleted"));
            load();
            return null;

        } catch (ApiException failure) {
            if (failure.isNotAuthenticated() || failure.isForbidden()) {
                // On this page, 401 or 403 means the session ended underneath the user - these are
                // their own listings. Showing "not allowed" would be baffling; the honest answer
                // is that they need to sign in again.
                LOGGER.log(Level.FINE, () -> "dashboard delete refused with " + failure.getStatus()
                        + "; treating it as an expired session");
                error(failure.getMessage());
                return keepingMessages(LOGIN);
            }
            error(failure.getMessage());
            return null;
        }
    }

    /**
     * @param petId the listing to edit
     * @return the edit form, carrying the id through the redirect so the page is bookmarkable
     */
    public String edit(Long petId) {
        return "/editPet.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    /** @return this user's listings, newest first, every status */
    public List<PetDTO> getListings() {
        return listings;
    }

    /** @return whether there is nothing to show, so the page can offer the add form instead */
    public boolean isNoListings() {
        return listings.isEmpty();
    }

    /**
     * @param pet a listing
     * @return the CSS class that colours its status badge, so the table is scannable at a glance
     */
    public String statusStyle(PetDTO pet) {
        if (pet == null || pet.getStatus() == null) {
            return "tag";
        }
        return switch (pet.getStatus()) {
            case "ADOPTED" -> "tag tag-adopted";
            case "REMOVED" -> "tag tag-removed";
            default -> "tag";
        };
    }

    /** @return the listing's photograph, or the bundled placeholder — never a broken image */
    public String thumbnailOf(PetDTO pet) {
        if (pet == null || pet.getMainImageUrl() == null || pet.getMainImageUrl().isBlank()) {
            return PetManagedBean.PLACEHOLDER_IMAGE;
        }
        return pet.getMainImageUrl();
    }

    private static String keepingMessages(String outcome) {
        FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
        return outcome;
    }

    private static String message(String key) {
        FacesContext context = FacesContext.getCurrentInstance();
        return context.getApplication()
                .evaluateExpressionGet(context, "#{msg['" + key + "']}", String.class);
    }

    private static void error(String text) {
        add(FacesMessage.SEVERITY_ERROR, text);
    }

    private static void info(String text) {
        add(FacesMessage.SEVERITY_INFO, text);
    }

    private static void add(FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
    }
}
