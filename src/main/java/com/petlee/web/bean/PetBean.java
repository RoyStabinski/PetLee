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

    // Not transient: what CDI injects is the client proxy of a normal-scoped bean, which is
    // serializable; javac sees only the declared type. Transient would leave null after
    // the session or view is passivated and restored.
    @SuppressWarnings("serial") @Inject private PetApi petApi;
    @SuppressWarnings("serial") @Inject private CategoryApi categoryApi;

    // Always a serializable implementation (List.of, toList(), emptyList() or the ArrayList
    // JSON-B builds); javac sees only the interface.
    @SuppressWarnings("serial") private List<PetDTO> pets = List.of();
    @SuppressWarnings("serial") private List<CategoryDTO> categories = List.of();
    private Integer selectedCategoryId;
    private String selectedSize;
    private String selectedGender;
    private Integer minAge;
    private Integer maxAge;
    private String search;
    /** The owner's own listings, every status included; a serializable list, as above. */
    @SuppressWarnings("serial") private List<PetDTO> myListings;

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

    /**
     * Re-reads the gallery with the current filters. An inverted age range is refused here,
     * without a call; {@code PetService} still refuses it for other REST clients.
     *
     * @return null, or the login page if the API refused an expired token
     */
    public String applyFilter() {
        if (minAge != null && maxAge != null && minAge > maxAge) {
            Messages.error("Age from must not be greater than Age to");
            return null;
        }
        return load();
    }

    /** @return null, or the login page if the API refused an expired token */
    public String clearFilters() {
        selectedCategoryId = null;
        selectedSize = null;
        selectedGender = null;
        minAge = null;
        maxAge = null;
        search = null;
        return load();
    }

    private String load() {
        try {
            pets = petApi.gallery(selectedCategoryId, selectedSize, selectedGender, minAge, maxAge,
                    search);
        } catch (ApiException e) {
            if (e.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            Messages.error(e.getMessage());
        }
        return null;
    }

    /**
     * @param petId a listing
     * @return the details page for it, as a redirect
     */
    /** @return the pet's main photograph, or the placeholder when it has none */
    public String imageUrlOf(PetDTO pet) {
        return imageOf(pet == null ? null : pet.imageUrl());
    }

    public String viewDetails(Long petId) {
        return "/petDetails.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    /**
     * @param imageUrl a listing's {@code imageUrl}, may be null or blank
     * @return that photograph, or the bundled placeholder
     */
    static String imageOf(String imageUrl) {
        return imageUrl == null || imageUrl.isBlank() ? PLACEHOLDER_IMAGE : imageUrl;
    }

    /**
     * @param status a listing's status string, as the API returns it; may be null
     * @return the CSS classes that colour its status badge
     */
    static String statusClassOf(String status) {
        if (status == null) {
            return "badge";
        }
        return switch (status) {
            case "AVAILABLE" -> "badge badge-available";
            case "ADOPTED" -> "badge badge-adopted";
            case "REMOVED" -> "badge badge-withdrawn";
            default -> "badge";
        };
    }

    /**
     * @param age a listing's age in whole years; may be null
     * @return the gallery card's age chip: "Under 1 yr", "1 yr", "N yrs", or "" when unknown
     */
    static String ageLabelOf(Integer age) {
        if (age == null) {
            return "";
        }
        if (age < 1) {
            return "Under 1 yr";
        }
        return age == 1 ? "1 yr" : age + " yrs";
    }

    /**
     * @param pet a gallery listing
     * @return its age chip, or "" when unknown
     */
    public String ageOf(PetDTO pet) { return ageLabelOf(pet == null ? null : pet.age()); }

    /** @return the size filter's menu: "Any size" plus each size */
    public List<SelectItem> getSizeOptions() {
        return options("Any size", "SMALL", "MEDIUM", "LARGE");
    }

    /** @return the gender filter's menu: "Any gender" plus each gender */
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

    /** @return whether the owner has no listings at all */
    public boolean isNoListings() {
        return getMyListings().isEmpty();
    }

    /** @return how many listings this user has, in every status, for the profile sidebar */
    public int getListingCount() {
        return getMyListings().size();
    }

    /** @return how many of them are still available, for the profile sidebar */
    public long getActiveListingCount() {
        return getMyListings().stream().filter(pet -> "AVAILABLE".equals(pet.status())).count();
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

    /**
     * Marks one of the caller's listings adopted, which takes it out of the public gallery.
     *
     * @param petId   the listing
     * @param version its {@code version} as the dashboard read it
     * @return null to stay on the dashboard, or the login page if the token has expired
     */
    public String markAdopted(Long petId, Long version) {
        return changeOwnStatus(petId, "ADOPTED", version,
                "The listing is marked as adopted and no longer appears in the gallery.");
    }

    /**
     * Undoes {@link #markAdopted}, putting the listing back in the public gallery.
     *
     * @param petId   the listing
     * @param version its {@code version} as the dashboard read it
     * @return null to stay on the dashboard, or the login page if the token has expired
     */
    public String markAvailable(Long petId, Long version) {
        return changeOwnStatus(petId, "AVAILABLE", version,
                "The listing is available again and back in the gallery.");
    }

    private String changeOwnStatus(Long petId, String status, Long version, String done) {
        try {
            petApi.changeStatus(petId, status, version);
            Messages.info(done);
            myListings = null;
            return null;
        } catch (ApiException e) {
            if (e.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            // 409 withdrawn, already so, or changed meanwhile: say why, and show the current state.
            Messages.error(e.getMessage());
            myListings = null;
            return null;
        }
    }

    /**
     * @param petId one of the owner's listings
     * @return its edit page, as a redirect
     */
    public String edit(Long petId) {
        return "/editPet.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    /**
     * @param pet a listing
     * @return the CSS classes of its status badge
     */
    public String statusStyle(PetDTO pet) { return statusClassOf(pet == null ? null : pet.status()); }

    // -------------------------------------------------------------------------------- properties

    /** @return the gallery, after the filters */
    public List<PetDTO> getPets() { return pets; }

    /** @return every category, for the gallery filter */
    public List<CategoryDTO> getCategories() { return categories; }

    /** @return whether no listing matches the gallery filters */
    public boolean isEmpty() { return pets.isEmpty(); }

    /** @return the category filter, or null for any */
    public Integer getSelectedCategoryId() { return selectedCategoryId; }

    /** @param v the category filter, or null for any */
    public void setSelectedCategoryId(Integer v) { this.selectedCategoryId = v; }

    /** @return the size filter, or null for any */
    public String getSelectedSize() { return selectedSize; }

    /** @param v the size filter, or null for any */
    public void setSelectedSize(String v) { this.selectedSize = v; }

    /** @return the gender filter, or null for any */
    public String getSelectedGender() { return selectedGender; }

    /** @param v the gender filter, or null for any */
    public void setSelectedGender(String v) { this.selectedGender = v; }

    /** @return the youngest age to show, or null for no lower bound */
    public Integer getMinAge() { return minAge; }

    /** @param v the youngest age to show, or null for no lower bound */
    public void setMinAge(Integer v) { this.minAge = v; }

    /** @return the oldest age to show, or null for no upper bound */
    public Integer getMaxAge() { return maxAge; }

    /** @param v the oldest age to show, or null for no upper bound */
    public void setMaxAge(Integer v) { this.maxAge = v; }

    /** @return text the name or the breed must contain, or null for any */
    public String getSearch() { return search; }

    /** @param v text the name or the breed must contain, or null for any */
    public void setSearch(String v) { this.search = v; }

}
