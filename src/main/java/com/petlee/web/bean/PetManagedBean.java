package com.petlee.web.bean;

import com.petlee.dto.CategoryDTO;
import com.petlee.dto.PetDTO;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The gallery and its filters — {@code #{petBean}}.
 *
 * <h2>{@code @ViewScoped}, not {@code @SessionScoped}</h2>
 * A filter belongs to the page being looked at. In session scope a user who narrowed the gallery to
 * small female cats would find that filter still applied in another tab, or an hour later after
 * following a link from an email, with no visible reason why the catalogue looked almost empty.
 *
 * <h2>Filtering happens on the server</h2>
 * {@link #applyFilter()} re-queries through {@link ApiClient#getPets}, which puts the three values
 * on the query string the contract defines. There is deliberately no code here that walks
 * {@link #pets} and removes entries: that would defeat T-08's indexes, and it would quietly become
 * wrong the moment the catalogue outgrew a single response.
 *
 * <h2>Failures are visible</h2>
 * Every call is wrapped, and an {@link ApiException} becomes a {@link FacesMessage}. A swallowed
 * exception here would render as an empty grid — indistinguishable from "no pets match", and the
 * user would keep clearing filters that were never the problem.
 */
@Named("petBean")
@ViewScoped
public class PetManagedBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(PetManagedBean.class.getName());

    /**
     * Shown for a listing with no photograph. A bundled file, so it cannot 404 and cannot be
     * fetched from anywhere else; the path is the one Faces serves resources from.
     */
    static final String PLACEHOLDER_IMAGE = "/resources/images/placeholder-pet.png";

    /** The contract's exact strings. Sent as submitted — T-22 parses these, not their labels. */
    private static final String[] SIZES = {"SMALL", "MEDIUM", "LARGE"};
    private static final String[] GENDERS = {"MALE", "FEMALE"};

    @Inject
    private ApiClient api;

    private List<PetDTO> pets = Collections.emptyList();
    private List<CategoryDTO> categories = Collections.emptyList();

    // null means "any", which is also what the contract means by an absent query parameter.
    private Integer selectedCategoryId;
    private String selectedSize;
    private String selectedGender;

    /**
     * Loads the vocabulary once and the catalogue unfiltered.
     *
     * <p>The two calls are separate {@code try} blocks on purpose: a failure to load categories
     * leaves the filter menu short but the gallery perfectly usable, and there is no reason for one
     * to take the other down with it.
     */
    @PostConstruct
    void init() {
        try {
            categories = api.getCategories();
        } catch (ApiException failure) {
            report(failure);
        }
        load();
    }

    /**
     * Re-queries with the current filter values.
     *
     * <p>Bound to the Apply button, and re-rendered over AJAX by T-29 so only the grid is replaced.
     */
    public void applyFilter() {
        load();
    }

    /** Resets every filter to "any" and reloads. */
    public void clearFilters() {
        selectedCategoryId = null;
        selectedSize = null;
        selectedGender = null;
        load();
    }

    private void load() {
        try {
            pets = api.getPets(selectedCategoryId, selectedSize, selectedGender);
            LOGGER.log(Level.FINE, () -> "gallery loaded: " + pets.size() + " listings");
        } catch (ApiException failure) {
            // Not an empty list: an empty grid would read as "nothing matches" and send the user
            // hunting through filters that were never the problem.
            report(failure);
        }
    }

    /**
     * Where the card links to.
     *
     * <p>{@code includeViewParams} carries {@code id} through the redirect, so the detail page can
     * be bookmarked and shared — which a POST-only navigation would not allow.
     *
     * @param petId the listing to open
     * @return the outcome for the detail page
     */
    public String viewDetails(Long petId) {
        return "/petDetails.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    /**
     * @param pet a listing
     * @return its main photograph, or the bundled placeholder. Never {@code null}, so the grid
     *         cannot render a broken image icon.
     */
    public String getMainImageUrl(PetDTO pet) {
        if (pet == null || pet.getMainImageUrl() == null || pet.getMainImageUrl().isBlank()) {
            return PLACEHOLDER_IMAGE;
        }
        return pet.getMainImageUrl();
    }

    /** @return whether the grid has nothing to draw, so the page can say so in words */
    public boolean isEmpty() {
        return pets.isEmpty();
    }

    /**
     * @return the size menu: "Any" plus the contract's three values
     * @see #options(String[], String)
     */
    public List<SelectItem> getSizeOptions() {
        return options(SIZES, "size.");
    }

    /** @return the gender menu: "Any" plus the contract's two values */
    public List<SelectItem> getGenderOptions() {
        return options(GENDERS, "gender.");
    }

    /**
     * Builds a filter menu.
     *
     * <p>The <strong>value</strong> of each item is the contract's raw uppercase name, because that
     * is what goes on the query string and what T-22 parses; only the <strong>label</strong> is
     * localised. Sending the label would produce {@code ?size=Small} and a 400
     * {@code INVALID_FILTER}, and the mistake would only show up in a language other than English.
     *
     * <p>The "Any" item's value is {@code null}, which {@link ApiClient#getPets} omits from the
     * query string entirely — the contract's way of saying "no restriction".
     *
     * @param values     the contract's enum names
     * @param keyPrefix  the bundle prefix for their labels, such as {@code size.}
     * @return the menu, "Any" first
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
        LOGGER.log(Level.FINE, () -> "gallery call failed: " + failure.getStatus() + " " + failure.getCode());
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, failure.getMessage(), null));
    }

    private static String bundle(String key) {
        FacesContext context = FacesContext.getCurrentInstance();
        return context.getApplication()
                .evaluateExpressionGet(context, "#{msg['" + key + "']}", String.class);
    }

    // -------------------------------------------------------------------------------- properties

    public List<PetDTO> getPets() {
        return pets;
    }

    public List<CategoryDTO> getCategories() {
        return categories;
    }

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
}
