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
import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Backs {@code admin.xhtml}: the moderation table and the category vocabulary.
 * The services remain the authorisation boundary; nothing here checks a role to decide a call.
 */
@Named("adminBean")
@ViewScoped
public class AdminBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter CREATED_ON =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String[] SIZES = {"SMALL", "MEDIUM", "LARGE"};
    private static final String[] SIZE_LABELS = {"Small", "Medium", "Large"};
    private static final String[] GENDERS = {"MALE", "FEMALE"};
    private static final String[] GENDER_LABELS = {"Male", "Female"};
    private static final String[] STATUSES = {"AVAILABLE", "ADOPTED", "REMOVED"};
    private static final String[] STATUS_LABELS = {"Available", "Adopted", "Withdrawn"};

    @Inject private transient PetService petService;
    @Inject private transient CategoryService categoryService;

    /** Not transient: see UserBean's own field for why. */
    @Inject
    private UserBean userBean;

    private List<Pet> listings = Collections.emptyList();
    private List<Category> categories = Collections.emptyList();

    /**
     * How many listings each category holds, keyed by id — the delete guard's evidence.
     * Counted over every listing, so the table's filters cannot distort it.
     */
    private Map<Integer, Long> listingsPerCategory = Map.of();

    private Integer selectedCategoryId;
    private String selectedSize;
    private String selectedGender;
    private String selectedStatus;
    private String newCategoryName;

    @PostConstruct
    void init() {
        load();
    }

    // ------------------------------------------------------------------------------- listings

    /** @return null — re-reads the table with the current filters and stays on the page */
    public String applyFilter() {
        load();
        return null;
    }

    /** @return null — clears every filter and re-reads */
    public String clearFilters() {
        selectedCategoryId = null;
        selectedSize = null;
        selectedGender = null;
        selectedStatus = null;
        load();
        return null;
    }

    /**
     * Hides a listing from the public gallery. Reversible.
     *
     * @param petId the listing
     * @return null
     */
    public String hide(Long petId) {
        return changeStatus(petId, "REMOVED", "The listing is hidden from the gallery. Restore puts it back.");
    }

    /**
     * Puts a hidden listing back in the public gallery.
     *
     * @param petId the listing
     * @return null
     */
    public String restore(Long petId) {
        return changeStatus(petId, "AVAILABLE", "The listing is public again.");
    }

    private String changeStatus(Long petId, String status, String successMessage) {
        try {
            petService.changeStatus(petId, status);
            Messages.info(successMessage);
            load();
        } catch (AppException failure) {
            Messages.error(failure.getMessage());
        }
        return null;
    }

    /**
     * Deletes a listing permanently, with its photograph. The page confirms first.
     *
     * @param petId the listing
     * @return null
     */
    public String deletePet(Long petId) {
        try {
            petService.delete(petId, userBean.getCurrentUserId(), userBean.isAdmin());
            Messages.info("The listing has been deleted permanently.");
            load();
        } catch (AppException failure) {
            Messages.error(failure.getMessage());
        }
        return null;
    }

    // ----------------------------------------------------------------------------- categories

    /** @return null — adds a category to the vocabulary the gallery and add-pet form read */
    public String addCategory() {
        try {
            Category created = categoryService.create(newCategoryName);
            Messages.info("Category added: " + created.getCategoryName());
            newCategoryName = null;
            load();
        } catch (AppException failure) {
            Messages.error(failure.getMessage());
        }
        return null;
    }

    /**
     * Deletes a category. The count can go stale between render and click, so a refusal is
     * reported as a message rather than prevented here.
     *
     * @param categoryId the category
     * @return null
     */
    public String deleteCategory(Integer categoryId) {
        try {
            categoryService.delete(categoryId);
            Messages.info("The category has been deleted.");
            load();
        } catch (AppException failure) {
            Messages.error(failure.getMessage());
        }
        return null;
    }

    /**
     * @param category a category
     * @return how many listings reference it, hidden ones included
     */
    public long listingCount(Category category) {
        if (category == null || category.getCategoryId() == null) {
            return 0L;
        }
        return listingsPerCategory.getOrDefault(category.getCategoryId(), 0L);
    }

    /**
     * @param category a category
     * @return whether deleting it would be refused
     */
    public boolean isInUse(Category category) {
        return listingCount(category) > 0;
    }

    // ---------------------------------------------------------------------------------- reading

    private void load() {
        try {
            List<Pet> all = petService.findAllForAdmin(selectedCategoryId,
                    selectedSize == null ? null : Pet.PetSize.valueOf(selectedSize),
                    selectedGender == null ? null : Pet.PetGender.valueOf(selectedGender));
            listings = all.stream().filter(this::matchesStatus).toList();
            listingsPerCategory = petService.countListingsByCategory();
            categories = categoryService.findAll();
        } catch (AppException failure) {
            Messages.error(failure.getMessage());
        }
    }

    private boolean matchesStatus(Pet pet) {
        return selectedStatus == null || selectedStatus.isEmpty()
                || selectedStatus.equals(pet.getStatus().name());
    }

    // --------------------------------------------------------------------------- presentation

    public List<Pet> getListings() { return listings; }

    public boolean isNoListings() { return listings.isEmpty(); }

    public List<Category> getCategories() { return categories; }

    /**
     * @param pet a listing
     * @return whether it is currently hidden from the public gallery
     */
    public boolean isHidden(Pet pet) {
        return pet != null && pet.getStatus() == Pet.PetStatus.REMOVED;
    }

    public String statusStyle(Pet pet) { return PetBean.statusClassOf(pet); }

    public String thumbnailOf(Pet pet) { return PetBean.imageOf(pet); }

    /**
     * @param pet a listing
     * @return its creation date as {@code yyyy-MM-dd HH:mm}, or an empty string
     */
    public String createdOn(Pet pet) {
        if (pet == null || pet.getCreatedAt() == null) {
            return "";
        }
        return CREATED_ON.format(pet.getCreatedAt());
    }

    /**
     * Builds the delete confirmation, escaped for the JavaScript {@code confirm()} it sits inside.
     *
     * @param pet the listing about to be deleted
     * @return the question to put to the administrator
     */
    public String confirmDelete(Pet pet) {
        String name = pet == null || pet.getPetName() == null ? "" : pet.getPetName();
        return MessageFormat.format(
                        "Delete {0} permanently? The listing and its photographs cannot be recovered.", name)
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }

    /** @return the category menu: "Any" plus every category */
    public List<SelectItem> getCategoryOptions() {
        List<SelectItem> items = new ArrayList<>(categories.size() + 1);
        items.add(new SelectItem(null, "Any"));
        for (Category category : categories) {
            items.add(new SelectItem(category.getCategoryId(), category.getCategoryName()));
        }
        return items;
    }

    public List<SelectItem> getSizeOptions() { return options(SIZES, SIZE_LABELS); }

    public List<SelectItem> getGenderOptions() { return options(GENDERS, GENDER_LABELS); }

    public List<SelectItem> getStatusOptions() { return options(STATUSES, STATUS_LABELS); }

    /** Builds a filter menu of "Any" plus each raw value under the label a moderator reads. */
    private static List<SelectItem> options(String[] values, String[] labels) {
        List<SelectItem> items = new ArrayList<>(values.length + 1);
        items.add(new SelectItem(null, "Any"));
        for (int i = 0; i < values.length; i++) {
            items.add(new SelectItem(values[i], labels[i]));
        }
        return items;
    }

    // -------------------------------------------------------------------------------- properties

    public Integer getSelectedCategoryId() { return selectedCategoryId; }

    public void setSelectedCategoryId(Integer selectedCategoryId) { this.selectedCategoryId = selectedCategoryId; }

    public String getSelectedSize() { return selectedSize; }

    public void setSelectedSize(String selectedSize) { this.selectedSize = selectedSize; }

    public String getSelectedGender() { return selectedGender; }

    public void setSelectedGender(String selectedGender) { this.selectedGender = selectedGender; }

    public String getSelectedStatus() { return selectedStatus; }

    public void setSelectedStatus(String selectedStatus) {
        this.selectedStatus = selectedStatus == null ? null
                : selectedStatus.trim().toUpperCase(Locale.ROOT);
    }

    public String getNewCategoryName() { return newCategoryName; }

    public void setNewCategoryName(String newCategoryName) { this.newCategoryName = newCategoryName; }
}
