package com.petlee.web.bean;

import com.petlee.dto.CategoryDTO;
import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetForm;
import com.petlee.web.client.ApiException;
import com.petlee.web.client.CategoryApi;
import com.petlee.web.client.PetApi;

import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Backs {@code addPet.xhtml} and {@code editPet.xhtml}, reading and writing through the REST API.
 * The fields are held individually because a {@code PetForm} record cannot be the target of
 * two-way Facelets binding.
 *
 * <p>The categories load from a view action ({@link #loadCategories}, or {@link #load} on the
 * edit page) rather than {@code @PostConstruct}: the template renders the messages before the
 * content, so a failure reported during rendering would never be seen.
 */
@Named("petFormBean")
@ViewScoped
public class PetFormBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** A redirect, so a refresh cannot repeat the submission. */
    private static final String DASHBOARD = "/profile.xhtml?faces-redirect=true";

    private static final String[] SIZES = {"SMALL", "MEDIUM", "LARGE"};
    private static final String[] GENDERS = {"MALE", "FEMALE"};

    /** Not transient: application-scoped proxies are serializable. */
    @Inject private PetApi petApi;
    @Inject private CategoryApi categoryApi;

    private String name;
    private String breed;
    private Integer age;
    private String size;
    private String gender;
    private String shortDesc;
    private String longDesc;
    private Integer categoryId;

    private List<CategoryDTO> categories = Collections.emptyList();

    /** Set by the edit page's view parameter; null means "creating". */
    private Long petId;


    /** The listing's version when the edit page loaded it; sent back so a stale save is refused. */
    private Long version;
    private transient Part uploadedFile;

    /**
     * The add page's view action: fills the category menu.
     *
     * @return null to render the page, or the login page if the API refused an expired token
     */
    public String loadCategories() {
        try {
            categories = categoryApi.findAll();
        } catch (ApiException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                return Messages.sessionExpired();
            }
            Messages.error(failure.getMessage());
        }
        return null;
    }

    /**
     * Loads an existing listing into the form, for the edit page's {@code <f:viewAction>}.
     * Non-owners meet the 403 page here rather than a form they could not save; the API's
     * {@code ownedByCaller} decides, and the update endpoint checks again.
     *
     * @return null to render the form; the response is already complete otherwise
     */
    public String load() {
        if (petId == null) {
            return fail(HttpServletResponse.SC_NOT_FOUND);
        }
        try {
            PetDetailDTO pet = petApi.detail(petId);

            if (!pet.ownedByCaller()) {
                return fail(HttpServletResponse.SC_FORBIDDEN);
            }

            name = pet.name();
            breed = pet.breed();
            age = pet.age();
            size = pet.size();
            gender = pet.gender();
            shortDesc = pet.shortDesc();
            longDesc = pet.longDesc();
            categoryId = pet.categoryId();
            version = pet.version();

        } catch (ApiException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
                return fail(HttpServletResponse.SC_NOT_FOUND);
            }
            if (failure.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                // An expired token, not someone else's listing: log in again rather than 403.
                return Messages.sessionExpired();
            }
            return reportAndStay(failure);
        }
        return loadCategories();
    }

    /**
     * Creates or updates the listing, then uploads the photograph if one was chosen.
     *
     * @return the dashboard, or null to stay on the form
     */
    public String save() {
        return petId == null ? create() : update();
    }

    private String create() {
        PetDTO created;
        try {
            created = petApi.create(form());
        } catch (ApiException failure) {
            return failure.getStatus() == 401 ? Messages.sessionExpired() : reportAndStay(failure);
        }

        if (!hasFile()) {
            return done("Your listing has been added.");
        }

        try (InputStream content = uploadedFile.getInputStream()) {
            petApi.uploadImage(created.id(), content, uploadedFile.getSubmittedFileName(),
                    uploadedFile.getContentType());
            return done("Your listing and its photograph have been added.");

        } catch (ApiException | IOException photographFailed) {
            // The listing exists but the photograph does not, and the edit page has no upload
            // control. Say both, so the user does not re-enter the form and create a duplicate.
            Messages.warn("Your listing was added without its photograph, which could not be stored. "
                    + "A photograph cannot be added to a listing afterwards. " + photographFailed.getMessage());
            return Messages.keep(DASHBOARD);
        }
    }

    private String update() {
        try {
            petApi.update(petId, form(), version);
            return done("Your listing has been updated.");

        } catch (ApiException failure) {
            if (failure.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            if (failure.getStatus() == 409) {
                Messages.error("This listing was changed by someone else. Reload it and try again.");
                return null;
            }
            return reportAndStay(failure);
        }
    }

    private PetForm form() {
        return new PetForm(name, breed, age, size, gender, shortDesc, longDesc, categoryId);
    }

    private String done(String text) {
        Messages.info(text);
        return Messages.keep(DASHBOARD);
    }

    // ------------------------------------------------------------------------------ the menus

    public List<CategoryDTO> getCategories() { return categories; }

    public List<SelectItem> getSizeOptions() { return options(SIZES); }

    public List<SelectItem> getGenderOptions() { return options(GENDERS); }

    /** @return whether the form is editing an existing listing rather than creating one */
    public boolean isEditing() { return petId != null; }

    /** @return the upload limits, shown before the user picks a file */
    public String getUploadLimits() { return "JPEG, PNG, WebP or GIF, up to 5 MB."; }

    // ------------------------------------------------------------------------------- internals

    private boolean hasFile() {
        return uploadedFile != null && uploadedFile.getSize() > 0;
    }

    /** Ends the response with an error status, letting the container serve its error page. */
    private String fail(int status) {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            context.getExternalContext().responseSendError(status, null);
        } catch (IOException connectionGone) {
            // The client hung up mid-response. Nothing useful can be sent.
        }
        context.responseComplete();
        return null;
    }

    /** A 400's message names the field and the rule, so it is shown as the server wrote it. */
    private String reportAndStay(ApiException failure) {
        Messages.error(failure.getMessage());
        return null;
    }

    private static List<SelectItem> options(String[] values) {
        List<SelectItem> items = new ArrayList<>(values.length);
        for (String value : values) {
            items.add(new SelectItem(value, value.charAt(0) + value.substring(1).toLowerCase()));
        }
        return items;
    }

    // ------------------------------------------------------------------------ form properties

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getBreed() { return breed; }

    public void setBreed(String breed) { this.breed = breed; }

    public Integer getAge() { return age; }

    public void setAge(Integer age) { this.age = age; }

    public String getSize() { return size; }

    public void setSize(String size) { this.size = size; }

    public String getGender() { return gender; }

    public void setGender(String gender) { this.gender = gender; }

    public String getShortDesc() { return shortDesc; }

    public void setShortDesc(String shortDesc) { this.shortDesc = shortDesc; }

    public String getLongDesc() { return longDesc; }

    public void setLongDesc(String longDesc) { this.longDesc = longDesc; }

    public Integer getCategoryId() { return categoryId; }

    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }

    public Long getPetId() { return petId; }

    public void setPetId(Long petId) { this.petId = petId; }

    public Part getUploadedFile() { return uploadedFile; }

    public void setUploadedFile(Part uploadedFile) { this.uploadedFile = uploadedFile; }
}
