package com.petlee.web.bean;

import com.petlee.dto.CategoryDTO;
import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetForm;
import com.petlee.dto.PetImageDTO;
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

    /** Matches the API's own limit, so the form can refuse before uploading anything. */
    private static final int MAX_PHOTOS = 5;

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

    /** The files chosen in either page's {@code <h:inputFile multiple="true">}. */
    private transient List<Part> uploadedFiles;

    /** The edit page's photographs, main included, oldest first. */
    private List<PetImageDTO> images = Collections.emptyList();

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
            images = pet.images();

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
        List<Part> files = chosenFiles();
        // Checked before anything is created, so a refused form leaves no listing behind.
        if (files.size() > MAX_PHOTOS) {
            Messages.error("Choose at most " + MAX_PHOTOS + " photographs.");
            return null;
        }

        PetDTO created;
        try {
            created = petApi.create(form());
        } catch (ApiException failure) {
            return failure.getStatus() == 401 ? Messages.sessionExpired() : reportAndStay(failure);
        }

        if (files.isEmpty()) {
            return done("Your listing has been added.");
        }

        Upload upload = uploadAll(created.id(), files);
        if (upload.failed() == 0) {
            return done(files.size() == 1
                    ? "Your listing and its photograph have been added."
                    : "Your listing and its " + files.size() + " photographs have been added.");
        }
        // The listing exists either way. Say so, so the user does not re-enter the form and
        // create a duplicate, and point at the edit page, where photographs can be added.
        Messages.warn("Your listing was added, but " + upload.failed() + " of " + files.size()
                + " photographs could not be stored. You can add them from the listing's edit page. "
                + upload.firstFailure());
        return Messages.keep(DASHBOARD);
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

    // ------------------------------------------------------------------ the edit page's photos

    /**
     * Uploads the chosen files to the listing being edited, up to five photographs in all.
     *
     * @return null to stay on the edit page, or the login page if the token has expired
     */
    public String addPhotos() {
        List<Part> files = chosenFiles();
        if (files.isEmpty()) {
            Messages.error("Choose a photograph to upload.");
            return null;
        }
        int room = MAX_PHOTOS - images.size();
        if (files.size() > room) {
            Messages.error(room <= 0
                    ? "This listing already has " + MAX_PHOTOS + " photographs. Delete one first."
                    : "A listing can have at most " + MAX_PHOTOS + " photographs; you can add "
                            + room + " more.");
            return null;
        }

        Upload upload = uploadAll(petId, files);
        if (upload.sessionExpired()) {
            return Messages.sessionExpired();
        }
        if (upload.failed() == 0) {
            Messages.info(files.size() == 1 ? "The photograph has been added."
                    : files.size() + " photographs have been added.");
        } else {
            Messages.error(upload.failed() + " of " + files.size()
                    + " photographs could not be stored. " + upload.firstFailure());
        }
        return reloadImages();
    }

    /**
     * Makes a photograph the one the gallery shows.
     *
     * @param imageId the photograph
     * @return null to stay on the edit page, or the login page if the token has expired
     */
    public String setMainPhoto(Long imageId) {
        try {
            petApi.setMainImage(petId, imageId);
            Messages.info("The main photograph has been changed.");
        } catch (ApiException failure) {
            if (failure.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            Messages.error(failure.getMessage());
        }
        return reloadImages();
    }

    /**
     * Deletes a photograph. If it was the main one, the oldest remaining one becomes main.
     *
     * @param imageId the photograph
     * @return null to stay on the edit page, or the login page if the token has expired
     */
    public String deletePhoto(Long imageId) {
        try {
            petApi.deleteImage(petId, imageId);
            Messages.info("The photograph has been deleted.");
        } catch (ApiException failure) {
            if (failure.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            Messages.error(failure.getMessage());
        }
        return reloadImages();
    }

    /**
     * Re-reads the photographs after a change, or after a failure that may mean the page is out
     * of date. Only the photographs: the form fields and their version stay as the user left
     * them, and photograph changes do not move the version.
     *
     * @return null, or the login page if the token has expired
     */
    private String reloadImages() {
        try {
            images = petApi.detail(petId).images();
        } catch (ApiException failure) {
            if (failure.getStatus() == 401) {
                return Messages.sessionExpired();
            }
            Messages.error(failure.getMessage());
        }
        return null;
    }

    /** What happened to a batch of uploads. */
    private record Upload(int failed, String firstFailure, boolean sessionExpired) { }

    /**
     * Uploads each file in turn, carrying on past a failed one; the API makes the first stored
     * photograph of a listing its main one. Stops at a 401, since every later call would fail too.
     */
    private Upload uploadAll(Long id, List<Part> files) {
        int failed = 0;
        String firstFailure = null;
        for (int i = 0; i < files.size(); i++) {
            Part file = files.get(i);
            try (InputStream content = file.getInputStream()) {
                petApi.addImage(id, content, file.getSubmittedFileName(), file.getContentType());
            } catch (ApiException | IOException failure) {
                failed++;
                if (firstFailure == null) {
                    firstFailure = failure.getMessage();
                }
                if (failure instanceof ApiException api && api.getStatus() == 401) {
                    // The files not yet tried count as failed too.
                    return new Upload(failed + files.size() - i - 1, firstFailure, true);
                }
            }
        }
        return new Upload(failed, firstFailure == null ? "" : firstFailure, false);
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

    /** @return the upload limits, shown before the user picks files */
    public String getUploadLimits() {
        return "Up to " + MAX_PHOTOS + " photographs: JPEG, PNG, WebP or GIF, each up to 5 MB. "
                + "The first one is shown in the gallery.";
    }

    /** @return the edit page's photographs, main included, oldest first */
    public List<PetImageDTO> getImages() { return images; }

    /** @return whether the edit page may offer more uploads */
    public boolean isRoomForPhotos() { return images.size() < MAX_PHOTOS; }

    /** @return how many more photographs the listing can take */
    public int getPhotoSlotsLeft() { return Math.max(0, MAX_PHOTOS - images.size()); }

    // ------------------------------------------------------------------------------- internals

    /** @return the chosen files, without the empty part a browser sends for "no file" */
    private List<Part> chosenFiles() {
        if (uploadedFiles == null) {
            return List.of();
        }
        return uploadedFiles.stream().filter(part -> part != null && part.getSize() > 0).toList();
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

    public List<Part> getUploadedFiles() { return uploadedFiles; }

    public void setUploadedFiles(List<Part> uploadedFiles) { this.uploadedFiles = uploadedFiles; }
}
