package com.petlee.web.bean;

import com.petlee.exception.ForbiddenException;
import com.petlee.exception.PetLeeException;
import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.service.CategoryService;
import com.petlee.service.PetService;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The gallery and its filters, and (folded in from the former {@code MyListingsBean}) the owner's
 * dashboard — {@code #{petBean}}.
 *
 * <h2>{@code @ViewScoped}, not {@code @SessionScoped}</h2>
 * A filter belongs to the page being looked at. In session scope a user who narrowed the gallery to
 * small female cats would find that filter still applied in another tab, or an hour later after
 * following a link from an email, with no visible reason why the catalogue looked almost empty.
 *
 * <h2>Filtering happens on the server</h2>
 * {@link #applyFilter()} re-queries {@link PetService#findGallery(Integer, Pet.PetSize,
 * Pet.PetGender)} directly. There is deliberately no code here that walks {@link #pets} and
 * removes entries.
 */
@Named("petBean")
@ViewScoped
public class PetBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Shown for a listing with no photograph. A bundled file, so it cannot 404 and cannot be
     * fetched from anywhere else.
     */
    static final String PLACEHOLDER_IMAGE = "/resources/images/placeholder-pet.png";

    private static final String LOGIN = "/login.xhtml?faces-redirect=true";

    @Inject private transient PetService petService;
    @Inject private transient CategoryService categoryService;

    /** Not {@code transient}: see {@link UserBean}'s own field for why. */
    @Inject private UserBean userBean;

    private List<Pet> pets = List.of();
    private List<Category> categories = List.of();
    private Integer selectedCategoryId;
    private String selectedSize;
    private String selectedGender;

    /** The owner's own listings, every status included; loaded lazily by {@link #getMyListings()}. */
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
        } catch (PetLeeException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        }
    }

    public String viewDetails(Long petId) {
        return "/petDetails.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    public String imageUrlOf(Pet pet) {
        String url = pet == null ? null : pet.getImageUrl();
        return url == null || url.isBlank() ? PLACEHOLDER_IMAGE : url;
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

    /** @return this user's listings, newest first, every status; loaded on first use */
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
     * @return {@code null} to stay on the dashboard, or the login page if the session has gone
     */
    public String delete(Long petId) {
        try {
            petService.delete(petId, userBean.getCurrentUserId(), userBean.isAdmin());
            myListings = null;
            return null;
        } catch (ForbiddenException noLongerAllowed) {
            // On this page these are the caller's own listings; a refusal here means the session
            // ended underneath them, and the honest answer is that they need to sign in again.
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, noLongerAllowed.getMessage(), null));
            FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
            return LOGIN;
        } catch (PetLeeException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
            return null;
        }
    }

    public String edit(Long petId) {
        return "/editPet.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    public String statusStyle(Pet pet) {
        if (pet == null || pet.getStatus() == null) {
            return "tag";
        }
        return switch (pet.getStatus()) {
            case ADOPTED -> "tag tag-adopted";
            case REMOVED -> "tag tag-removed";
            default -> "tag";
        };
    }

    public String thumbnailOf(Pet pet) {
        return imageUrlOf(pet);
    }

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
