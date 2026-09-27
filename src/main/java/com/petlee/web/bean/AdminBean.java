package com.petlee.web.bean;

import com.petlee.dto.AdminPetDTO;
import com.petlee.dto.CategoryDTO;
import com.petlee.web.client.AdminApi;
import com.petlee.web.client.ApiException;
import com.petlee.web.client.CategoryApi;

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
 * Backs {@code admin.xhtml}: the moderation table and the category vocabulary, through the REST
 * API. The API remains the authorisation boundary; nothing here checks a role to decide a call.
 *
 * <p>Loaded from a view action ({@link #loadPage}) rather than {@code @PostConstruct}: the
 * template renders the messages before the content, so a failure reported during rendering
 * would never be seen.
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

    /** Not transient: application-scoped proxies are serializable. */
    @Inject private AdminApi adminApi;
    @Inject private CategoryApi categoryApi;

    private List<AdminPetDTO> listings = Collections.emptyList();
    private List<CategoryDTO> categories = Collections.emptyList();

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

    /**
     * The page's view action.
     *
     * @return null to render the page, or the login page if the API refused an expired token
     */
    public String loadPage() {
        return load();
    }

    // ------------------------------------------------------------------------------- listings

    /** @return null — re-reads the table with the current filters and stays on the page */
    public String applyFilter() {
        return load();
    }

    /** @return null — clears every filter and re-reads */
    public String clearFilters() {
        selectedCategoryId = null;
        selectedSize = null;
        selectedGender = null;
        selectedStatus = null;
        return load();
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
            adminApi.changeStatus(petId, status);
            Messages.info(successMessage);
            return load();
        } catch (ApiException failure) {
            return report(failure);
        }
    }

    /**
     * Deletes a listing permanently, with its photograph. The page confirms first.
     *
     * @param petId the listing
     * @return null
     */
    public String deletePet(Long petId) {
        try {
            adminApi.deletePet(petId);
            Messages.info("The listing has been deleted permanently.");
            return load();
        } catch (ApiException failure) {
            return report(failure);
        }
    }

    // ----------------------------------------------------------------------------- categories

    /** @return null — adds a category to the vocabulary the gallery and add-pet form read */
    public String addCategory() {
        try {
            CategoryDTO created = adminApi.createCategory(newCategoryName);
            Messages.info("Category added: " + created.name());
            newCategoryName = null;
            return load();
        } catch (ApiException failure) {
            return report(failure);
        }
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
            adminApi.deleteCategory(categoryId);
            Messages.info("The category has been deleted.");
            return load();
        } catch (ApiException failure) {
            return report(failure);
        }
    }

    /**
     * @param category a category
     * @return how many listings reference it, hidden ones included
     */
    public long listingCount(CategoryDTO category) {
        if (category == null || category.id() == null) {
            return 0L;
        }
        return listingsPerCategory.getOrDefault(category.id(), 0L);
    }

    /**
     * @param category a category
     * @return whether deleting it would be refused
     */
    public boolean isInUse(CategoryDTO category) {
        return listingCount(category) > 0;
    }

    // ---------------------------------------------------------------------------------- reading

    /** @return null, or the login page if the API refused an expired token */
    private String load() {
        try {
            listings = adminApi.listings(selectedCategoryId, selectedSize, selectedGender)
                    .stream().filter(this::matchesStatus).toList();
            listingsPerCategory = adminApi.categoryCounts();
            categories = categoryApi.findAll();
            return null;
        } catch (ApiException failure) {
            return report(failure);
        }
    }

    private boolean matchesStatus(AdminPetDTO pet) {
        return selectedStatus == null || selectedStatus.isEmpty()
                || selectedStatus.equals(pet.status());
    }

    /**
     * Shows a refusal as a message and stays, except that an expired token goes to log in again.
     *
     * @return null, or the login page
     */
    private static String report(ApiException failure) {
        if (failure.getStatus() == 401) {
            return Messages.sessionExpired();
        }
        Messages.error(failure.getMessage());
        return null;
    }

    // --------------------------------------------------------------------------- presentation

    public List<AdminPetDTO> getListings() { return listings; }

    public boolean isNoListings() { return listings.isEmpty(); }

    public List<CategoryDTO> getCategories() { return categories; }

    /**
     * @param pet a listing
     * @return whether it is currently hidden from the public gallery
     */
    public boolean isHidden(AdminPetDTO pet) {
        return pet != null && "REMOVED".equals(pet.status());
    }

    public String statusStyle(AdminPetDTO pet) {
        return PetBean.statusClassOf(pet == null ? null : pet.status());
    }

    public String thumbnailOf(AdminPetDTO pet) {
        return PetBean.imageOf(pet == null ? null : pet.imageUrl());
    }

    /**
     * @param pet a listing
     * @return its creation date as {@code yyyy-MM-dd HH:mm}, or an empty string
     */
    public String createdOn(AdminPetDTO pet) {
        if (pet == null || pet.createdAt() == null) {
            return "";
        }
        return CREATED_ON.format(pet.createdAt());
    }

    /**
     * Builds the delete confirmation, escaped for the JavaScript {@code confirm()} it sits inside.
     *
     * @param pet the listing about to be deleted
     * @return the question to put to the administrator
     */
    public String confirmDelete(AdminPetDTO pet) {
        String name = pet == null || pet.name() == null ? "" : pet.name();
        return MessageFormat.format(
                        "Delete {0} permanently? The listing and its photographs cannot be recovered.", name)
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }

    /** @return the category menu: "Any" plus every category */
    public List<SelectItem> getCategoryOptions() {
        List<SelectItem> items = new ArrayList<>(categories.size() + 1);
        items.add(new SelectItem(null, "Any"));
        for (CategoryDTO category : categories) {
            items.add(new SelectItem(category.id(), category.name()));
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
