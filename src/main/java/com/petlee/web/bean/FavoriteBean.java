package com.petlee.web.bean;

import com.petlee.dto.PetDTO;
import com.petlee.web.client.ApiException;
import com.petlee.web.client.FavoriteApi;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The member's saved pets, through {@code /api/favorites}: the hearts, the "My favourites" card,
 * the favourites page and the count in the navigation bar.
 *
 * <p>View-scoped, and the saved ids are fetched once per page and then kept up to date locally,
 * so a gallery of twenty cards asks the API once, not twenty times. A guest never calls the API:
 * everything reads as "nothing saved", and a heart sends them to log in.
 */
@Named("favoriteBean")
@ViewScoped
public class FavoriteBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(FavoriteBean.class.getName());

    /** How many saved pets the "My favourites" card lists before "See all". */
    private static final int PANEL_SIZE = 5;

    private static final String LOGIN = "/login.xhtml?faces-redirect=true";

    /** Not transient: see UserBean's own field for why. */
    @Inject private FavoriteApi favoriteApi;
    @Inject private UserBean userBean;

    /** The saved pet ids; null until first needed. */
    private Set<Long> ids;

    /** The saved pets, newest first; null until first needed, and after any change. */
    private List<PetDTO> favorites;

    /**
     * Whether the last attempt to read the list failed. Kept apart from an empty list, so a
     * failure says so instead of showing "nothing saved yet" beside a count that says otherwise.
     */
    private boolean loadFailed;

    /**
     * View action for the pages that show the list (home and favourites): loads it before
     * rendering, so a failure is reported where the messages are drawn.
     *
     * @return null to render the page, or the login page if the API refused an expired token
     */
    public String load() {
        if (!userBean.isLoggedIn()) {
            return null;
        }
        try {
            favorites = favoriteApi.list();
            loadFailed = false;
            ids = null; // re-read with the list, so the two agree
            loadIds();
            return null;
        } catch (ApiException e) {
            favorites = List.of();
            if (e.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            // Reported where the list would be ("could not be loaded"), not as a page-wide error.
            loadFailed = true;
            LOGGER.log(Level.WARNING, "favourites could not be read", e);
            return null;
        }
    }

    /**
     * Saves the pet, or forgets it if it is already saved. Bound to the heart with f:ajax.
     *
     * @param petId the pet
     * @return null to stay (the ajax call re-renders the heart); the login page for a guest, or
     *         when the API refused an expired token
     */
    public String toggle(Long petId) {
        if (!userBean.isLoggedIn()) {
            Messages.warn("Log in to save favourites");
            return Messages.keep(LOGIN);
        }
        return change(petId, !isSaved(petId));
    }

    /**
     * Forgets a saved pet: the (x) in the "My favourites" card.
     *
     * @param petId the pet
     * @return null to stay, or the login page if the API refused an expired token
     */
    public String remove(Long petId) {
        if (!userBean.isLoggedIn()) {
            return null;
        }
        return change(petId, false);
    }

    private String change(Long petId, boolean save) {
        try {
            if (save) {
                favoriteApi.add(petId);
                loadIds().add(petId);
            } else {
                favoriteApi.remove(petId);
                loadIds().remove(petId);
            }
            favorites = null;
            return null;
        } catch (ApiException e) {
            if (e.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            if (e.getStatus() == 404) {
                Messages.error("This pet is no longer available, so it cannot be saved.");
            } else {
                Messages.error(e.getMessage());
            }
            return null;
        }
    }

    /**
     * @param petId a pet
     * @return whether the member has saved it; always false for a guest
     */
    public boolean isSaved(Long petId) {
        return petId != null && loadIds().contains(petId);
    }

    /** @return how many pets the member has saved, for the navigation bar and the card */
    public int getCount() {
        return loadIds().size();
    }

    /** @return the saved pets, newest saved first; empty for a guest */
    public List<PetDTO> getFavorites() {
        if (favorites == null) {
            favorites = List.of();
            loadFailed = false;
            if (userBean.isLoggedIn()) {
                try {
                    favorites = favoriteApi.list();
                } catch (ApiException e) {
                    // Too late in the page for a message; the page shows loadFailed instead.
                    loadFailed = true;
                    LOGGER.log(Level.WARNING, "favourites could not be read", e);
                }
            }
        }
        return favorites;
    }

    /** @return whether the list could not be read; the pages then say so instead of "empty" */
    public boolean isLoadFailed() {
        getFavorites();
        return loadFailed;
    }

    /** @return at most the first five saved pets, for the card on the home page */
    public List<PetDTO> getPanelFavorites() {
        List<PetDTO> all = getFavorites();
        return all.size() <= PANEL_SIZE ? all : all.subList(0, PANEL_SIZE);
    }

    /** @return whether the member really has nothing saved: false when the list failed to load */
    public boolean isNoFavorites() {
        return getFavorites().isEmpty() && !loadFailed;
    }

    /**
     * The saved ids, fetched on first use in this view. Called from getters during rendering,
     * where a message could no longer be shown, so a failure reads as "nothing saved".
     */
    private Set<Long> loadIds() {
        if (ids == null) {
            ids = new HashSet<>();
            if (userBean.isLoggedIn()) {
                try {
                    ids.addAll(favoriteApi.ids());
                } catch (ApiException e) {
                    LOGGER.log(Level.FINE, "saved ids could not be read", e);
                }
            }
        }
        return ids;
    }
}
