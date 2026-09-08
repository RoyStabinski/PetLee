package com.petlee.web.bean;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.service.AppException;
import com.petlee.service.CategoryService;
import com.petlee.service.PetService;

import jakarta.annotation.PostConstruct;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Backs the public gallery and the owner's dashboard. View-scoped, so a filter belongs to the
 * page being looked at; filtering is a fresh query, never an in-memory pass over {@link #pets}.
 */
@Named("petBean")
@ViewScoped
public class PetBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Shown for a listing with no photograph. Bundled, so it cannot 404. */
    static final String PLACEHOLDER_IMAGE = "/resources/images/placeholder-pet.png";

    private static final String LOGIN = "/login.xhtml?faces-redirect=true";

    @Inject private transient PetService petService;
    @Inject private transient CategoryService categoryService;

    /** Not transient: see UserBean's own field for why. */
    @Inject private UserBean userBean;

    private List<Pet> pets = List.of();
    private List<Category> categories = List.of();
    private Integer selectedCategoryId;
    private String selectedSize;
    private String selectedGender;

    /** The owner's own listings, every status included; loaded on first use. */
    private List<Pet> myListings;

    @PostConstruct
    void init() {
        categories = categoryService.findAll();
        load();
    }

    public void applyFilter() {
        load();
    }

    public void clearFilters() {
        selectedCategoryId = null;
        selectedSize = null;
        selectedGender = null;
        load();
    }

    private void load() {
        try {
            pets = petService.findGallery(selectedCategoryId,
                    selectedSize == null ? null : Pet.PetSize.valueOf(selectedSize),
                    selectedGender == null ? null : Pet.PetGender.valueOf(selectedGender));
        } catch (AppException e) {
            Messages.error(e.getMessage());
        }
    }

    public String viewDetails(Long petId) {
        return "/petDetails.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    public String imageUrlOf(Pet pet) { return imageOf(pet); }

    /**
     * @param pet a listing, may be null
     * @return its photograph, or the bundled placeholder
     */
    static String imageOf(Pet pet) {
        String url = pet == null ? null : pet.getImageUrl();
        return url == null || url.isBlank() ? PLACEHOLDER_IMAGE : url;
    }

    /**
     * @param pet a listing, may be null
     * @return the CSS class that colours its status badge
     */
    static String statusClassOf(Pet pet) {
        if (pet == null || pet.getStatus() == null) {
            return "tag";
        }
        return switch (pet.getStatus()) {
            case ADOPTED -> "tag tag-adopted";
            case REMOVED -> "tag tag-removed";
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

    /** @return this user's listings, newest first, in every status */
    public List<Pet> getMyListings() {
        if (myListings == null) {
            Long ownerId = userBean.getCurrentUserId();
            myListings = petService.findByOwner(ownerId);
        }
        return myListings;
    }

    public boolean isNoListings() {
        return getMyListings().isEmpty();
    }

    /**
     * Removes one of the caller's own listings and refreshes the dashboard.
     *
     * @param petId the listing to remove
     * @return null to stay on the dashboard, or the login page if the session has gone
     */
    public String delete(Long petId) {
        try {
            petService.delete(petId, userBean.getCurrentUserId(), userBean.isAdmin());
            myListings = null;
            return null;
        } catch (AppException e) {
            Messages.error(e.getMessage());
            // These are the caller's own listings, so a refusal means the session ended.
            return e.getStatus() == 403 ? Messages.keep(LOGIN) : null;
        }
    }

    public String edit(Long petId) {
        return "/editPet.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    public String statusStyle(Pet pet) { return statusClassOf(pet); }

    // -------------------------------------------------------------------------------- properties

    public List<Pet> getPets() { return pets; }
    public List<Category> getCategories() { return categories; }
    public boolean isEmpty() { return pets.isEmpty(); }
    public Integer getSelectedCategoryId() { return selectedCategoryId; }
    public void setSelectedCategoryId(Integer v) { this.selectedCategoryId = v; }
    public String getSelectedSize() { return selectedSize; }
    public void setSelectedSize(String v) { this.selectedSize = v; }
    public String getSelectedGender() { return selectedGender; }
    public void setSelectedGender(String v) { this.selectedGender = v; }
}
