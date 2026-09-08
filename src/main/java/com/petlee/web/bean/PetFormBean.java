package com.petlee.web.bean;

import com.petlee.dto.CategoryDTO;
import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetForm;
import com.petlee.web.client.ApiClient;
import com.petlee.web.client.ApiException;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creating and editing a listing — {@code #{petFormBean}}, behind both {@code addPet.xhtml} and
 * {@code editPet.xhtml}.
 *
 * <h2>Two calls that cannot be one transaction</h2>
 * Creating a listing with a photograph is {@code POST /api/pets} followed by
 * {@code POST /api/pets/{id}/images}. Two HTTP requests cannot be made atomic, so the interesting
 * case is the one in the middle: <strong>the pet was created and the photograph was not</strong>.
 * {@link #save()} does not pretend either that everything worked or that nothing did. It says what
 * happened and lands the user on their dashboard, where the listing is waiting and the photograph
 * can be added again.
 *
 * <h2>Client-side validation is a courtesy</h2>
 * The page repeats T-15's rules so a typo costs no round trip. The server remains the authority and
 * its message is always what the user is shown; nothing here decides whether a listing is valid.
 */
@Named("petFormBean")
@ViewScoped
public class PetFormBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(PetFormBean.class.getName());

    /** After a save. A redirect, so a refresh cannot repeat the submission. */
    private static final String DASHBOARD = "/profile.xhtml?faces-redirect=true";

    private static final String[] SIZES = {"SMALL", "MEDIUM", "LARGE"};
    private static final String[] GENDERS = {"MALE", "FEMALE"};

    @Inject
    private ApiClient api;

    @Inject
    private UserManagedBean userBean;

    private final PetForm form = new PetForm();

    private List<CategoryDTO> categories = Collections.emptyList();

    /** Set on the edit page by its view parameter; {@code null} means "creating". */
    private Long petId;

    private transient Part uploadedFile;

    @PostConstruct
    void init() {
        try {
            categories = api.getCategories();
        } catch (ApiException failure) {
            report(failure);
        }
    }

    /**
     * Loads an existing listing into the form — the {@code <f:viewAction>} behind
     * {@code editPet.xhtml}.
     *
     * <p>Ownership is checked here so a non-owner meets the 403 page rather than a filled-in form
     * they cannot save. That is a courtesy, not the control: {@code PUT /api/pets/{id}} refuses a
     * non-owner regardless (T-15, T-22), and this check exists only so the refusal arrives before
     * the user has typed anything.
     *
     * @return {@code null} to render the form; the response is already complete otherwise
     */
    public String load() {
        if (petId == null) {
            return fail(HttpServletResponse.SC_NOT_FOUND);
        }
        try {
            PetDetailDTO pet = api.getPet(petId);

            if (!isOwnedByCurrentUser(pet)) {
                LOGGER.log(Level.FINE, () -> "user " + userBean.getCurrentUserId()
                        + " tried to edit listing " + petId);
                return fail(HttpServletResponse.SC_FORBIDDEN);
            }

            form.setName(pet.getName());
            form.setBreed(pet.getBreed());
            form.setAge(pet.getAge());
            form.setSize(pet.getSize());
            form.setGender(pet.getGender());
            form.setShortDesc(pet.getShortDesc());
            form.setLongDesc(pet.getLongDesc());
            form.setCategoryId(categoryIdOf(pet.getCategoryName()));
            return null;

        } catch (ApiException failure) {
            return failure.isNotFound() ? fail(HttpServletResponse.SC_NOT_FOUND) : reportAndStay(failure);
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
        PetDTO created;
        try {
            created = api.createPet(form);
        } catch (ApiException failure) {
            return reportAndStay(failure);
        }

        if (!hasFile()) {
            return done("pet.saved.created");
        }

        try {
            api.uploadImage(created.getId(), uploadedFile.getInputStream(),
                    uploadedFile.getSubmittedFileName(), true);
            return done("pet.saved.createdWithPhoto");

        } catch (ApiException | IOException photographFailed) {
            // The listing exists. Saying "that failed" would be a lie the user would act on by
            // filling the whole form in again, and creating a duplicate. Saying "that worked"
            // would leave them wondering where the photograph went. So: what worked, what did
            // not, and where to go to try again.
            LOGGER.log(Level.INFO, photographFailed,
                    () -> "listing " + created.getId() + " created but its photograph was not stored");
            warn(message("pet.saved.createdWithoutPhoto") + " " + reasonOf(photographFailed));
            return keepingMessages(DASHBOARD);
        }
    }

    private String update() {
        try {
            api.updatePet(petId, form);
            return done("pet.saved.updated");

        } catch (ApiException failure) {
            if (failure.isConflict()) {
                // The user-visible face of specification §4's concurrency control. A generic
                // error here would leave them with no idea what to do; this says exactly what.
                error(message("pet.saved.conflict"));
                return null;
            }
            return reportAndStay(failure);
        }
    }

    private String done(String messageKey) {
        info(message(messageKey));
        // A redirect, not a forward: a refresh on a forwarded POST re-submits it, and the user
        // ends up with two identical listings and no idea which one is theirs.
        return keepingMessages(DASHBOARD);
    }

    // ------------------------------------------------------------------------------ the menus

    public List<CategoryDTO> getCategories() {
        return categories;
    }

    public List<SelectItem> getSizeOptions() {
        return options(SIZES, "size.");
    }

    public List<SelectItem> getGenderOptions() {
        return options(GENDERS, "gender.");
    }

    /** @return whether the form is editing an existing listing rather than creating one */
    public boolean isEditing() {
        return petId != null;
    }

    /** @return the upload limits, so the user reads them before choosing a file rather than after */
    public String getUploadLimits() {
        return message("pet.photo.limits");
    }

    // ------------------------------------------------------------------------------- internals

    private boolean hasFile() {
        return uploadedFile != null && uploadedFile.getSize() > 0;
    }

    /**
     * @param pet the listing being opened for editing
     * @return whether the signed-in user owns it
     *         <p>{@code ownerEmail} is only populated for a logged-in caller, and the contract
     *         carries no owner id, so the comparison is by email — the one owner field that is
     *         both present and unique (T-06 enforces that).
     */
    private boolean isOwnedByCurrentUser(PetDetailDTO pet) {
        return userBean.getCurrentUser() != null
                && pet.getOwnerEmail() != null
                && pet.getOwnerEmail().equalsIgnoreCase(userBean.getCurrentUser().getEmail());
    }

    private Integer categoryIdOf(String name) {
        return categories.stream()
                .filter(category -> category.getName().equals(name))
                .map(CategoryDTO::getId)
                .findFirst()
                .orElse(null);
    }

    private static String reasonOf(Exception failure) {
        return failure instanceof ApiException api ? api.getMessage() : "";
    }

    private String fail(int status) {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            context.getExternalContext().responseSendError(status, null);
        } catch (IOException connectionGone) {
            LOGGER.log(Level.FINE, "could not send " + status, connectionGone);
        }
        context.responseComplete();
        return null;
    }

    private String reportAndStay(ApiException failure) {
        report(failure);
        return null;
    }

    private void report(ApiException failure) {
        error(failure.getMessage());
    }

    private static List<SelectItem> options(String[] values, String keyPrefix) {
        List<SelectItem> items = new ArrayList<>(values.length);
        for (String value : values) {
            items.add(new SelectItem(value, message(keyPrefix + value)));
        }
        return items;
    }

    private static String keepingMessages(String outcome) {
        FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
        return outcome;
    }

    private static String message(String key) {
        FacesContext context = FacesContext.getCurrentInstance();
        return context.getApplication()
                .evaluateExpressionGet(context, "#{msg['" + key + "']}", String.class);
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

    public PetForm getForm() {
        return form;
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
