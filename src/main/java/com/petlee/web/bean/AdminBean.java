package com.petlee.web.bean;

import com.petlee.dto.AdminPetDTO;
import com.petlee.dto.CategoryDTO;
import com.petlee.web.client.ApiClient;
import com.petlee.web.client.ApiException;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The administrator's panel — {@code #{adminBean}}, behind {@code admin.xhtml}.
 *
 * <h2>It is not a boundary</h2>
 * Three things keep this page away from a member: T-25 does not draw the menu entry, T-33's filter
 * answers 403 to the page, and T-34's {@code @AdminOnly} answers 403 to every call this bean makes.
 * Only the last of those is a security boundary; the other two exist so that a member never sees a
 * door they cannot open. Nothing here checks a role, because a check here could disagree with the
 * server's and would be believed by nobody.
 *
 * <h2>Where the category counts come from</h2>
 * The listing table already holds every pet in every status, so the number of listings in a
 * category is a count of those rows by name — no endpoint and no second round trip. It is a
 * courtesy, not a guarantee: the count can go stale between render and click, which is why
 * {@code DELETE /api/categories/{id}} still answers 409 and why that answer is shown as a sentence.
 */
@Named("adminBean")
@ViewScoped
public class AdminBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(AdminBean.class.getName());

    /** The contract's enum strings. Only the labels are localised. */
    private static final String[] SIZES = {"SMALL", "MEDIUM", "LARGE"};
    private static final String[] GENDERS = {"MALE", "FEMALE"};
    private static final String[] STATUSES = {"AVAILABLE", "ADOPTED", "REMOVED"};

    @Inject
    private ApiClient api;

    private List<AdminPetDTO> listings = Collections.emptyList();
    private List<CategoryDTO> categories = Collections.emptyList();

    /** How many listings each category holds, keyed by name — the delete guard's evidence. */
    private Map<String, Long> listingsPerCategory = Map.of();

    private Integer selectedCategoryId;
    private String selectedSize;
    private String selectedGender;

    /**
     * Filtered here rather than on the server: the moderation table is one page of rows, and an
     * endpoint that can be asked for a single status is an endpoint that can be asked for none.
     */
    private String selectedStatus;

    private String newCategoryName;

    @PostConstruct
    void init() {
        load();
    }

    // ------------------------------------------------------------------------------- listings

    /** Re-reads the table with the current filters. @return {@code null} — stay on the page */
    public String applyFilter() {
        load();
        return null;
    }

    /** Clears every filter and re-reads. @return {@code null} */
    public String clearFilters() {
        selectedCategoryId = null;
        selectedSize = null;
        selectedGender = null;
        selectedStatus = null;
        load();
        return null;
    }

    /**
     * Hides a listing — the ordinary, reversible moderation action.
     *
     * @param petId the listing
     * @return {@code null}
     */
    public String hide(Long petId) {
        return changeStatus(petId, "REMOVED", "admin.hidden");
    }

    /**
     * Puts a hidden listing back in the public gallery.
     *
     * @param petId the listing
     * @return {@code null}
     */
    public String restore(Long petId) {
        return changeStatus(petId, "AVAILABLE", "admin.restored");
    }

    private String changeStatus(Long petId, String status, String messageKey) {
        try {
            api.setPetStatus(petId, status);
            info(message(messageKey));
            load();
        } catch (ApiException failure) {
            report(failure);
        }
        return null;
    }

    /**
     * Removes a listing permanently, with its photographs. The page confirms first, naming the pet.
     *
     * @param petId the listing
     * @return {@code null}
     */
    public String deletePet(Long petId) {
        try {
            api.deletePet(petId);
            info(message("admin.deleted"));
            load();
        } catch (ApiException failure) {
            report(failure);
        }
        return null;
    }

    // ----------------------------------------------------------------------------- categories

    /**
     * Adds a category to the vocabulary the gallery filter and the add-pet form both read.
     *
     * @return {@code null}
     */
    public String addCategory() {
        try {
            CategoryDTO created = api.createCategory(newCategoryName);
            info(message("admin.categoryAdded") + " " + created.getName());
            newCategoryName = null;
            load();
        } catch (ApiException failure) {
            report(failure);
        }
        return null;
    }

    /**
     * Removes a category. The page disables the button for one that holds listings, but the count
     * can change between render and click, so the server's 409 is shown as a plain sentence.
     *
     * @param categoryId the category
     * @return {@code null}
     */
    public String deleteCategory(Integer categoryId) {
        try {
            api.deleteCategory(categoryId);
            info(message("admin.categoryDeleted"));
            load();
        } catch (ApiException failure) {
            report(failure);
        }
        return null;
    }

    /**
     * @param category a category
     * @return how many listings reference it, {@code REMOVED} ones included — they hold the foreign
     *         key too, so they are what a delete would trip over
     */
    public long listingCount(CategoryDTO category) {
        if (category == null || category.getName() == null) {
            return 0L;
        }
        return listingsPerCategory.getOrDefault(category.getName(), 0L);
    }

    /** @param category a category @return whether deleting it would be refused */
    public boolean isInUse(CategoryDTO category) {
        return listingCount(category) > 0;
    }

    // ---------------------------------------------------------------------------------- reading

    private void load() {
        try {
            List<AdminPetDTO> all = api.getAllPets(selectedCategoryId, selectedSize, selectedGender);
            listings = all.stream().filter(this::matchesStatus).toList();
            listingsPerCategory = all.stream()
                    .filter(pet -> pet.getCategoryName() != null)
                    .collect(Collectors.groupingBy(AdminPetDTO::getCategoryName,
                            Collectors.counting()));
            categories = api.getCategories();

            LOGGER.log(Level.FINE, () -> "admin panel loaded: " + listings.size() + " listings, "
                    + categories.size() + " categories");
        } catch (ApiException failure) {
            report(failure);
        }
    }

    private boolean matchesStatus(AdminPetDTO pet) {
        return selectedStatus == null || selectedStatus.isEmpty()
                || selectedStatus.equals(pet.getStatus());
    }

    // --------------------------------------------------------------------------- presentation

    /** @return the listings the filters select, newest first, in every status */
    public List<AdminPetDTO> getListings() {
        return listings;
    }

    /** @return whether the table has nothing to draw, so the page can say so in words */
    public boolean isNoListings() {
        return listings.isEmpty();
    }

    /** @return the whole category vocabulary, alphabetically */
    public List<CategoryDTO> getCategories() {
        return categories;
    }

    /** @param pet a listing @return whether it is currently hidden from the public gallery */
    public boolean isHidden(AdminPetDTO pet) {
        return pet != null && "REMOVED".equals(pet.getStatus());
    }

    /** @param pet a listing @return the CSS class that colours its status badge */
    public String statusStyle(AdminPetDTO pet) {
        if (pet == null || pet.getStatus() == null) {
            return "tag";
        }
        return switch (pet.getStatus()) {
            case "ADOPTED" -> "tag tag-adopted";
            case "REMOVED" -> "tag tag-removed";
            default -> "tag";
        };
    }

    /** @param pet a listing @return its photograph, or the bundled placeholder */
    public String thumbnailOf(AdminPetDTO pet) {
        if (pet == null || pet.getMainImageUrl() == null || pet.getMainImageUrl().isBlank()) {
            return PetManagedBean.PLACEHOLDER_IMAGE;
        }
        return pet.getMainImageUrl();
    }

    /**
     * @param pet a listing
     * @return its creation date as {@code yyyy-MM-dd HH:mm}. The wire carries a full ISO-8601
     *         timestamp down to the nanosecond, which no moderator needs and which wraps the column.
     */
    public String createdOn(AdminPetDTO pet) {
        String iso = pet == null ? null : pet.getCreatedAt();
        if (iso == null || iso.length() < 16) {
            return iso == null ? "" : iso;
        }
        return iso.substring(0, 10) + " " + iso.substring(11, 16);
    }

    /**
     * The confirmation text for a permanent delete, naming the pet.
     *
     * <p>It is built here rather than in the page because it goes inside a JavaScript
     * {@code confirm()} in an {@code onclick} attribute: a listing called {@code Ol' Rex} would
     * otherwise close the string and break the button. Quotes and backslashes are escaped for that
     * one context and nothing else.
     *
     * @param pet the listing about to be deleted
     * @return the question to put to the administrator
     */
    public String confirmDelete(AdminPetDTO pet) {
        String name = pet == null || pet.getName() == null ? "" : pet.getName();
        return MessageFormat.format(bundle("admin.confirmDelete"), name)
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }

    /** @return the category menu: "Any" plus every category */
    public List<SelectItem> getCategoryOptions() {
        List<SelectItem> items = new ArrayList<>(categories.size() + 1);
        items.add(new SelectItem(null, bundle("gallery.filter.any")));
        for (CategoryDTO category : categories) {
            items.add(new SelectItem(category.getId(), category.getName()));
        }
        return items;
    }

    /** @return the size menu: "Any" plus the contract's three values */
    public List<SelectItem> getSizeOptions() {
        return options(SIZES, "size.");
    }

    /** @return the gender menu: "Any" plus the contract's two values */
    public List<SelectItem> getGenderOptions() {
        return options(GENDERS, "gender.");
    }

    /** @return the status menu: "Any" plus the contract's three values */
    public List<SelectItem> getStatusOptions() {
        return options(STATUSES, "status.");
    }

    /**
     * Builds a filter menu whose item values are the raw values the API takes and whose labels are
     * localised — the same rule as T-28's: sending a label produces {@code ?size=Small} and a 400.
     */
    private static List<SelectItem> options(String[] values, String keyPrefix) {
        List<SelectItem> items = new ArrayList<>(values.length + 1);
        items.add(new SelectItem(null, bundle("gallery.filter.any")));
        for (String value : values) {
            items.add(new SelectItem(value, bundle(keyPrefix + value)));
        }
        return items;
    }

    private void report(ApiException failure) {
        LOGGER.log(Level.FINE, () -> "admin call failed: " + failure.getStatus() + " "
                + failure.getCode());
        error(failure.getMessage());
    }

    private static String message(String key) {
        return bundle(key);
    }

    private static String bundle(String key) {
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

    // -------------------------------------------------------------------------------- properties

    public Integer getSelectedCategoryId() {
        return selectedCategoryId;
    }

    public void setSelectedCategoryId(Integer selectedCategoryId) {
        this.selectedCategoryId = selectedCategoryId;
    }

    public String getSelectedSize() {
        return selectedSize;
    }

    public void setSelectedSize(String selectedSize) {
        this.selectedSize = selectedSize;
    }

    public String getSelectedGender() {
        return selectedGender;
    }

    public void setSelectedGender(String selectedGender) {
        this.selectedGender = selectedGender;
    }

    public String getSelectedStatus() {
        return selectedStatus;
    }

    public void setSelectedStatus(String selectedStatus) {
        this.selectedStatus = selectedStatus == null ? null
                : selectedStatus.trim().toUpperCase(Locale.ROOT);
    }

    public String getNewCategoryName() {
        return newCategoryName;
    }

    public void setNewCategoryName(String newCategoryName) {
        this.newCategoryName = newCategoryName;
    }
}
