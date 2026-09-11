package com.petlee.web.bean;

import com.petlee.exception.ConflictException;
import com.petlee.exception.NotFoundException;
import com.petlee.exception.PetLeeException;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Creating and editing a listing — {@code #{petFormBean}}, behind both {@code addPet.xhtml} and
 * {@code editPet.xhtml}.
 *
 * <h2>Why this bean does not hold a {@code PetForm}</h2>
 * {@code PetForm} is a record now, in the DTO package that lives at the REST boundary, where
 * JSON-B builds one from a request body. A record has no setters and its accessor is
 * {@code name()}, not {@code getName()}, so it cannot be the target of
 * {@code <h:inputText value="...">} two-way binding, which needs both; and this whole package is
 * not permitted to reach into the DTO package at all, so this class cannot even name the type.
 * This bean therefore holds the eight fields individually, as ordinary mutable properties that
 * {@code petFields.xhtml} binds to directly ({@code #{petFormBean.name}}), and calls
 * {@link PetService}'s scalar overloads of {@code create} and {@code update}, which are the place
 * a {@code PetForm} gets assembled from them, inside the service package that is allowed to know
 * the type.
 *
 * <h2>Two calls that cannot be one transaction</h2>
 * Creating a listing with a photograph is {@link PetService#create} followed by
 * {@link PetService#attachImage}. The interesting case is the one in the middle:
 * <strong>the pet was created and the photograph was not</strong>. {@link #save()} does not
 * pretend either that everything worked or that nothing did. It says what happened and lands the
 * user on their dashboard, where the listing is waiting and the photograph can be added again.
 *
 * <h2>Client-side validation is a courtesy</h2>
 * The page repeats the service's rules so a typo costs no round trip. The server remains the
 * authority and its message is always what the user is shown; nothing here decides whether a
 * listing is valid.
 */
@Named("petFormBean")
@ViewScoped
public class PetFormBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** After a save. A redirect, so a refresh cannot repeat the submission. */
    private static final String DASHBOARD = "/profile.xhtml?faces-redirect=true";

    private static final String[] SIZES = {"SMALL", "MEDIUM", "LARGE"};
    private static final String[] GENDERS = {"MALE", "FEMALE"};

    @Inject private transient PetService petService;
    @Inject private transient CategoryService categoryService;

    /** Not {@code transient}: see {@link UserBean}'s own field for why. */
    @Inject
    private UserBean userBean;

    // Form backing — see the class documentation for why these are not a PetForm.
    private String name;
    private String breed;
    private Integer age;
    private String size;
    private String gender;
    private String shortDesc;
    private String longDesc;
    private Integer categoryId;

    private List<Category> categories = Collections.emptyList();

    /** Set on the edit page by its view parameter; {@code null} means "creating". */
    private Long petId;

    private transient Part uploadedFile;

    @PostConstruct
    void init() {
        try {
            categories = categoryService.findAll();
        } catch (PetLeeException failure) {
            report(failure);
        }
    }

    /**
     * Loads an existing listing into the form — the {@code <f:viewAction>} behind
     * {@code editPet.xhtml}.
     *
     * <p>Ownership is checked here so a non-owner meets the 403 page rather than a filled-in form
     * they cannot save. That is a courtesy, not the control: {@link PetService#update} refuses a
     * non-owner regardless, and this check exists only so the refusal arrives before the user has
     * typed anything.
     *
     * @return {@code null} to render the form; the response is already complete otherwise
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
            return null;

        } catch (NotFoundException noSuchPet) {
            return fail(HttpServletResponse.SC_NOT_FOUND);
        } catch (PetLeeException failure) {
            return reportAndStay(failure);
        }
    }

    /**
     * Creates or updates the listing, then uploads the photograph if one was chosen.
     *
     * @return the dashboard, or {@code null} to stay on the form
     */
    public String save() {
        return petId == null ? create() : update();
    }

    private String create() {
        Pet created;
        try {
            created = petService.create(name, breed, age, size, gender, shortDesc, longDesc,
                    categoryId, userBean.getCurrentUserId());
        } catch (PetLeeException failure) {
            return reportAndStay(failure);
        }

        if (!hasFile()) {
            return done("Your listing has been added.");
        }

        try {
            petService.attachImage(created.getPetId(), uploadedFile, userBean.getCurrentUserId());
            return done("Your listing and its photograph have been added.");

        } catch (PetLeeException | AppException photographFailed) {
            // The listing exists. Saying "that failed" would be a lie the user would act on by
            // filling the whole form in again, and creating a duplicate. Saying "that worked"
            // would leave them wondering where the photograph went. So: what worked, what did
            // not, and where to go to try again.
            warn("Your listing was added, but the photograph could not be stored. You can add it from"
                    + " here. " + reasonOf(photographFailed));
            return keepingMessages(DASHBOARD);
        }
    }

    private String update() {
        try {
            petService.update(petId, name, breed, age, size, gender, shortDesc, longDesc,
                    categoryId, userBean.getCurrentUserId());
            return done("Your listing has been updated.");

        } catch (ConflictException staleEdit) {
            // The user-visible face of specification §4's concurrency control. A generic error
            // here would leave them with no idea what to do; this says exactly what.
            error("This listing was changed by someone else. Reload it and try again.");
            return null;
        } catch (PetLeeException failure) {
            return reportAndStay(failure);
        }
    }

    private String done(String text) {
        info(text);
        // A redirect, not a forward: a refresh on a forwarded POST re-submits it, and the user
        // ends up with two identical listings and no idea which one is theirs.
        return keepingMessages(DASHBOARD);
    }

    // ------------------------------------------------------------------------------ the menus

    public List<Category> getCategories() {
        return categories;
    }

    public List<SelectItem> getSizeOptions() {
        return options(SIZES);
    }

    public List<SelectItem> getGenderOptions() {
        return options(GENDERS);
    }

    /** @return whether the form is editing an existing listing rather than creating one */
    public boolean isEditing() {
        return petId != null;
    }

    /** @return the upload limits, so the user reads them before choosing a file rather than after */
    public String getUploadLimits() {
        return "JPEG, PNG, WebP or GIF, up to 5 MB.";
    }

    // ------------------------------------------------------------------------------- internals

    private boolean hasFile() {
        return uploadedFile != null && uploadedFile.getSize() > 0;
    }

    /**
     * @param pet the listing being opened for editing
     * @return whether the signed-in user owns it
     */
    private boolean isOwnedByCurrentUser(Pet pet) {
        return userBean.getCurrentUserId() != null
                && pet.getOwner() != null
                && userBean.getCurrentUserId().equals(pet.getOwner().getUserId());
    }

    private static String reasonOf(RuntimeException failure) {
        if (failure instanceof PetLeeException business) {
            return business.getMessage();
        }
        if (failure instanceof AppException business) {
            return business.getMessage();
        }
        return "";
    }

    private String fail(int status) {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            context.getExternalContext().responseSendError(status, null);
        } catch (IOException connectionGone) {
            // The client hung up mid-response. Nothing useful can be sent and nothing is broken.
        }
        context.responseComplete();
        return null;
    }

    private String reportAndStay(PetLeeException failure) {
        report(failure);
        return null;
    }

    private void report(PetLeeException failure) {
        error(failure.getMessage());
    }

    private static List<SelectItem> options(String[] values) {
        List<SelectItem> items = new ArrayList<>(values.length);
        for (String value : values) {
            items.add(new SelectItem(value, value.charAt(0) + value.substring(1).toLowerCase()));
        }
        return items;
    }

    private static String keepingMessages(String outcome) {
        FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
        return outcome;
    }

    private static void error(String text) {
        add(FacesMessage.SEVERITY_ERROR, text);
    }

    private static void warn(String text) {
        add(FacesMessage.SEVERITY_WARN, text);
    }

    private static void info(String text) {
        add(FacesMessage.SEVERITY_INFO, text);
    }

    private static void add(FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
    }

    // ------------------------------------------------------------------------ form properties

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getShortDesc() {
        return shortDesc;
    }

    public void setShortDesc(String shortDesc) {
        this.shortDesc = shortDesc;
    }

    public String getLongDesc() {
        return longDesc;
    }

    public void setLongDesc(String longDesc) {
        this.longDesc = longDesc;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Long getPetId() {
        return petId;
    }

    public void setPetId(Long petId) {
        this.petId = petId;
    }

    public Part getUploadedFile() {
        return uploadedFile;
    }

    public void setUploadedFile(Part uploadedFile) {
        this.uploadedFile = uploadedFile;
    }

    /** @return the chosen file's bytes; package-visible so tests can stand in for a {@link Part} */
    InputStream fileStream() throws IOException {
        return uploadedFile.getInputStream();
    }
}
