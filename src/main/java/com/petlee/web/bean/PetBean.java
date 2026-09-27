package com.petlee.web.bean;

import com.petlee.dto.CategoryDTO;
import com.petlee.dto.PetDTO;
import com.petlee.web.client.ApiException;
import com.petlee.web.client.CategoryApi;
import com.petlee.web.client.PetApi;

import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Backs the public gallery and the owner's dashboard, both read through the REST API.
 * View-scoped, so a filter belongs to the page being looked at; filtering is a fresh query, never
 * an in-memory pass over {@link #pets}.
 *
 * <p>Each page loads what it needs from an {@code <f:viewAction>} ({@link #loadGallery} or
 * {@link #loadMyListings}) rather than from {@code @PostConstruct}: the template renders the
 * messages before the content, so a failure reported during rendering would never be seen.
 */
@Named("petBean")
@ViewScoped
public class PetBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Shown for a listing with no photograph. Bundled, so it cannot 404. */
    static final String PLACEHOLDER_IMAGE = "/resources/images/placeholder-pet.png";

    /** Not transient: application-scoped proxies are serializable. */
    @Inject private PetApi petApi;
    @Inject private CategoryApi categoryApi;

    private List<PetDTO> pets = List.of();
    private List<CategoryDTO> categories = List.of();
    private Integer selectedCategoryId;
    private String selectedSize;
    private String selectedGender;

    /** The owner's own listings, every status included; loaded on first use. */
    private List<PetDTO> myListings;

    /**
     * The gallery's view action: the filter's categories, then the unfiltered gallery.
     *
     * @return null to render the page, or the login page if the API refused an expired token
     */
    public String loadGallery() {
        try {
            categories = categoryApi.findAll();
        } catch (ApiException e) {
            if (e.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            Messages.error(e.getMessage());
        }
        return load();
    }

    /** @return null, or the login page if the API refused an expired token */
    public String applyFilter() {
        return load();
    }

    /** @return null, or the login page if the API refused an expired token */
    public String clearFilters() {
        selectedCategoryId = null;
        selectedSize = null;
        selectedGender = null;
        return load();
    }

    private String load() {
        try {
            pets = petApi.gallery(selectedCategoryId, selectedSize, selectedGender);
        } catch (ApiException e) {
            if (e.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            Messages.error(e.getMessage());
        }
        return null;
    }

    public String viewDetails(Long petId) {
        return "/petDetails.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    public String imageUrlOf(PetDTO pet) { return imageOf(pet == null ? null : pet.imageUrl()); }

    /**
     * @param imageUrl a listing's {@code imageUrl}, may be null or blank
     * @return that photograph, or the bundled placeholder
     */
    static String imageOf(String imageUrl) {
        return imageUrl == null || imageUrl.isBlank() ? PLACEHOLDER_IMAGE : imageUrl;
    }

    /**
     * @param status a listing's status string, as the API returns it; may be null
     * @return the CSS class that colours its status badge
     */
    static String statusClassOf(String status) {
        if (status == null) {
            return "tag";
        }
        return switch (status) {
            case "ADOPTED" -> "tag tag-adopted";
            case "REMOVED" -> "tag tag-removed";
            default -> "tag";
        };
    }

    public List<SelectItem> getSizeOptions() {
        return options("Any size", "SMALL", "MEDIUM", "LARGE");
    }

    public List<SelectItem> getGenderOptions() {
        return options("Any gender", "MALE", "FEMALE");
    }

    private static List<SelectItem> options(String anyLabel, String... values) {
        List<SelectItem> items = new ArrayList<>();
        items.add(new SelectItem(null, anyLabel));
        for (String v : values) {
            items.add(new SelectItem(v, v.charAt(0) + v.substring(1).toLowerCase()));
        }
        return items;
    }

    // ----------------------------------------------------------------------- the owner's dashboard

    /**
     * The dashboard's view action.
     *
     * @return null to render the page, or the login page if the API no longer accepts the token
     */
    public String loadMyListings() {
        try {
            myListings = petApi.mine();
            return null;
        } catch (ApiException e) {
            myListings = List.of();
            if (e.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            Messages.error(e.getMessage());
            return null;
        }
    }

    /**
     * @return this user's listings, newest first, in every status. Reloaded here only after
     *         {@link #delete} empties the cache; the view action does the first load.
     */
    public List<PetDTO> getMyListings() {
        if (myListings == null) {
            try {
                myListings = petApi.mine();
            } catch (ApiException e) {
                Messages.error(e.getMessage());
                myListings = List.of();
            }
        }
        return myListings;
    }

    public boolean isNoListings() {
        return getMyListings().isEmpty();
    }

    /**
     * Removes one of the caller's own listings through {@code DELETE /api/pets/{id}} and
     * refreshes the dashboard.
     *
     * @param petId the listing to remove
     * @return null to stay on the dashboard, or the login page if the token has expired
     */
    public String delete(Long petId) {
        try {
            petApi.delete(petId);
            myListings = null;
            return null;
        } catch (ApiException e) {
            if (e.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            // 403 not yours, 404 already gone, 409 edited meanwhile: say so and stay.
            Messages.error(e.getMessage());
            return null;
        }
    }

    public String edit(Long petId) {
        return "/editPet.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    public String statusStyle(PetDTO pet) { return statusClassOf(pet == null ? null : pet.status()); }

    // -------------------------------------------------------------------------------- properties

    public List<PetDTO> getPets() { return pets; }
    public List<CategoryDTO> getCategories() { return categories; }
    public boolean isEmpty() { return pets.isEmpty(); }
    public Integer getSelectedCategoryId() { return selectedCategoryId; }
    public void setSelectedCategoryId(Integer v) { this.selectedCategoryId = v; }
    public String getSelectedSize() { return selectedSize; }
    public void setSelectedSize(String v) { this.selectedSize = v; }
    public String getSelectedGender() { return selectedGender; }
    public void setSelectedGender(String v) { this.selectedGender = v; }
}
