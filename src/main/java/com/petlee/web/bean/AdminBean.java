package com.petlee.web.bean;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.service.AppException;
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
import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The administrator's panel — {@code #{adminBean}}, behind {@code admin.xhtml}.
 *
 * <h2>It is not a boundary</h2>
 * The page is not drawn for a non-administrator and the menu entry is not drawn either, but the
 * real boundary is the service layer: every call here passes the caller's id and admin flag,
 * learned from the session by {@link UserBean}, and {@link PetService#delete} refuses anyone who
 * is neither the owner nor an admin. Nothing here checks a role for the purpose of deciding
 * whether to make a call, because a check here could disagree with the service's and would be
 * believed by nobody.
 *
 * <h2>Where the category counts come from</h2>
 * The listing table already holds every pet in every status, so the number of listings in a
 * category is a count of those rows by name — no second round trip. It is a courtesy, not a
 * guarantee: the count can go stale between render and click, which is why
 * {@link CategoryService#delete} still answers with a conflict, shown as a sentence.
 */
@Named("adminBean")
@ViewScoped
public class AdminBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter CREATED_ON =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** The contract's enum strings, paired with the labels a moderator reads. */
    private static final String[] SIZES = {"SMALL", "MEDIUM", "LARGE"};
    private static final String[] SIZE_LABELS = {"Small", "Medium", "Large"};
    private static final String[] GENDERS = {"MALE", "FEMALE"};
    private static final String[] GENDER_LABELS = {"Male", "Female"};
    private static final String[] STATUSES = {"AVAILABLE", "ADOPTED", "REMOVED"};
    private static final String[] STATUS_LABELS = {"Available", "Adopted", "Withdrawn"};

    @Inject private transient PetService petService;
    @Inject private transient CategoryService categoryService;

    /** Not {@code transient}: see {@link UserBean}'s own field for why. */
    @Inject
    private UserBean userBean;

    private List<Pet> listings = Collections.emptyList();
    private List<Category> categories = Collections.emptyList();

    /** How many listings each category holds, keyed by name — the delete guard's evidence. */
    private Map<String, Long> listingsPerCategory = Map.of();

    private Integer selectedCategoryId;
    private String selectedSize;
    private String selectedGender;

    /**
     * Filtered here rather than by the service: the moderation table is one page of rows, and a
     * call that can be asked for a single status is a call that can be asked for none.
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
        return changeStatus(petId, "REMOVED", "The listing is hidden from the gallery. Restore puts it back.");
    }

    /**
     * Puts a hidden listing back in the public gallery.
     *
     * @param petId the listing
     * @return {@code null}
     */
    public String restore(Long petId) {
        return changeStatus(petId, "AVAILABLE", "The listing is public again.");
    }

    private String changeStatus(Long petId, String status, String successMessage) {
        try {
            petService.changeStatus(petId, status);
            info(successMessage);
            load();
        } catch (AppException failure) {
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
            petService.delete(petId, userBean.getCurrentUserId(), userBean.isAdmin());
            info("The listing has been deleted permanently.");
            load();
        } catch (AppException failure) {
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
            Category created = categoryService.create(newCategoryName);
            info("Category added: " + created.getCategoryName());
            newCategoryName = null;
            load();
        } catch (AppException failure) {
            report(failure);
        }
        return null;
    }

    /**
     * Removes a category. The page disables the button for one that holds listings, but the count
     * can change between render and click, so a conflict is shown as a plain sentence.
     *
     * @param categoryId the category
     * @return {@code null}
     */
    public String deleteCategory(Integer categoryId) {
        try {
            categoryService.delete(categoryId);
            info("The category has been deleted.");
            load();
        } catch (AppException failure) {
            report(failure);
        }
        return null;
    }

    /**
     * @param category a category
     * @return how many listings reference it, {@code REMOVED} ones included — they hold the foreign
     *         key too, so they are what a delete would trip over
     */
    public long listingCount(Category category) {
        if (category == null || category.getCategoryName() == null) {
            return 0L;
        }
        return listingsPerCategory.getOrDefault(category.getCategoryName(), 0L);
    }

    /** @param category a category @return whether deleting it would be refused */
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
            listingsPerCategory = all.stream()
                    .filter(pet -> pet.getCategory() != null && pet.getCategory().getCategoryName() != null)
                    .collect(Collectors.groupingBy(pet -> pet.getCategory().getCategoryName(),
                            Collectors.counting()));
            categories = categoryService.findAll();
        } catch (AppException failure) {
            report(failure);
        }
    }

    private boolean matchesStatus(Pet pet) {
        return selectedStatus == null || selectedStatus.isEmpty()
                || selectedStatus.equals(pet.getStatus().name());
    }

    // --------------------------------------------------------------------------- presentation

    /** @return the listings the filters select, newest first, in every status */
    public List<Pet> getListings() {
        return listings;
    }

    /** @return whether the table has nothing to draw, so the page can say so in words */
    public boolean isNoListings() {
        return listings.isEmpty();
    }

    /** @return the whole category vocabulary, alphabetically */
    public List<Category> getCategories() {
        return categories;
    }

    /** @param pet a listing @return whether it is currently hidden from the public gallery */
    public boolean isHidden(Pet pet) {
        return pet != null && pet.getStatus() == Pet.PetStatus.REMOVED;
    }

    /** @param pet a listing @return the CSS class that colours its status badge */
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

    /** @param pet a listing @return its photograph, or the bundled placeholder */
    public String thumbnailOf(Pet pet) {
        if (pet == null || pet.getImageUrl() == null || pet.getImageUrl().isBlank()) {
            return PetBean.PLACEHOLDER_IMAGE;
        }
        return pet.getImageUrl();
    }

    /**
     * @param pet a listing
     * @return its creation date as {@code yyyy-MM-dd HH:mm}. The entity carries a full timestamp
     *         down to the nanosecond, which no moderator needs and which wraps the column.
     */
    public String createdOn(Pet pet) {
        if (pet == null || pet.getCreatedAt() == null) {
            return "";
        }
        return CREATED_ON.format(pet.getCreatedAt());
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

    /** @return the size menu: "Any" plus the contract's three values */
    public List<SelectItem> getSizeOptions() {
        return options(SIZES, SIZE_LABELS);
    }

    /** @return the gender menu: "Any" plus the contract's two values */
    public List<SelectItem> getGenderOptions() {
        return options(GENDERS, GENDER_LABELS);
    }

    /** @return the status menu: "Any" plus the contract's three values */
    public List<SelectItem> getStatusOptions() {
        return options(STATUSES, STATUS_LABELS);
    }

    /**
     * Builds a filter menu whose item values are the raw values the service takes and whose labels
     * are the ones a moderator reads.
     */
    private static List<SelectItem> options(String[] values, String[] labels) {
        List<SelectItem> items = new ArrayList<>(values.length + 1);
        items.add(new SelectItem(null, "Any"));
        for (int i = 0; i < values.length; i++) {
            items.add(new SelectItem(values[i], labels[i]));
        }
        return items;
    }

    private void report(AppException failure) {
        error(failure.getMessage());
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
