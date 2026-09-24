package com.petlee.web.bean;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.service.AppException;
import com.petlee.service.CategoryService;
import com.petlee.service.PetService;

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import jakarta.validation.ConstraintViolationException;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Backs {@code addPet.xhtml} and {@code editPet.xhtml}. The fields are held individually
 * because a {@code PetForm} record cannot be the target of two-way Facelets binding.
 */
@Named("petFormBean")
@ViewScoped
public class PetFormBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** A redirect, so a refresh cannot repeat the submission. */
    private static final String DASHBOARD = "/profile.xhtml?faces-redirect=true";

    private static final String[] SIZES = {"SMALL", "MEDIUM", "LARGE"};
    private static final String[] GENDERS = {"MALE", "FEMALE"};

    @Inject private transient PetService petService;
    @Inject private transient CategoryService categoryService;

    /** Not transient: see UserBean's own field for why. */
    @Inject
    private UserBean userBean;

    private String name;
    private String breed;
    private Integer age;
    private String size;
    private String gender;
    private String shortDesc;
    private String longDesc;
    private Integer categoryId;

    private List<Category> categories = Collections.emptyList();

    /** Set by the edit page's view parameter; null means "creating". */
    private Long petId;


    /** The listing's version when the edit page loaded it; sent back so a stale save is refused. */
    private Long version;
    private transient Part uploadedFile;

    @PostConstruct
    void init() {
        try {
            categories = categoryService.findAll();
        } catch (AppException failure) {
            Messages.error(failure.getMessage());
        }
    }

    /**
     * Loads an existing listing into the form, for the edit page's {@code <f:viewAction>}.
     * Non-owners meet the 403 page here rather than a form they could not save.
     *
     * @return null to render the form; the response is already complete otherwise
     */
    public String load() {
        if (petId == null) {
            return fail(HttpServletResponse.SC_NOT_FOUND);
        }
        try {
            Pet pet = petService.findDetail(petId);

            if (!isOwnedByCurrentUser(pet)) {
                return fail(HttpServletResponse.SC_FORBIDDEN);
            }

            name = pet.getPetName();
            breed = pet.getBreed();
            age = pet.getAge();
            size = pet.getSize().name();
            gender = pet.getGender().name();
            shortDesc = pet.getShortDesc();
            longDesc = pet.getLongDesc();
            categoryId = pet.getCategory() == null ? null : pet.getCategory().getCategoryId();
            version = pet.getVersion();
            return null;

        } catch (AppException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
                return fail(HttpServletResponse.SC_NOT_FOUND);
            }
            return reportAndStay(failure);
        }
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
        Pet created;
        try {
            created = petService.create(name, breed, age, size, gender, shortDesc, longDesc,
                    categoryId, userBean.getCurrentUserId());
        } catch (ConstraintViolationException invalid) {
            return reportAndStay(invalid);
        } catch (AppException failure) {
            return reportAndStay(failure);
        }

        if (!hasFile()) {
            return done("Your listing has been added.");
        }

        try {
            petService.attachImage(created.getPetId(), uploadedFile, userBean.getCurrentUserId());
            return done("Your listing and its photograph have been added.");

        } catch (AppException photographFailed) {
            // The listing exists but the photograph does not, and the edit page has no upload
            // control. Say both, so the user does not re-enter the form and create a duplicate.
            Messages.warn("Your listing was added without its photograph, which could not be stored. "
                    + "A photograph cannot be added to a listing afterwards. " + photographFailed.getMessage());
            return Messages.keep(DASHBOARD);
        }
    }

    private String update() {
        try {
            petService.update(petId, name, breed, age, size, gender, shortDesc, longDesc,
                    categoryId, version, userBean.getCurrentUserId());
            return done("Your listing has been updated.");

        } catch (ConstraintViolationException invalid) {
            return reportAndStay(invalid);
        } catch (AppException failure) {
            if (failure.getStatus() == 409) {
                Messages.error("This listing was changed by someone else. Reload it and try again.");
                return null;
            }
            return reportAndStay(failure);
        }
    }

    private String done(String text) {
        Messages.info(text);
        return Messages.keep(DASHBOARD);
    }

    // ------------------------------------------------------------------------------ the menus

    public List<Category> getCategories() { return categories; }

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

    private boolean isOwnedByCurrentUser(Pet pet) {
        return userBean.getCurrentUserId() != null
                && pet.getOwner() != null
                && userBean.getCurrentUserId().equals(pet.getOwner().getUserId());
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

    private String reportAndStay(AppException failure) {
        Messages.error(failure.getMessage());
        return null;
    }

    private String reportAndStay(ConstraintViolationException invalid) {
        Messages.error(Messages.firstViolation(invalid, "That listing could not be saved."));
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
