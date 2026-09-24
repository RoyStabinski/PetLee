package com.petlee.service;

import com.petlee.dto.PetForm;
import com.petlee.model.Pet;
import com.petlee.model.User;
import com.petlee.repository.PetRepository;
import com.petlee.repository.UserRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.Part;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Locale;

/**
 * Pet listings: the catalogue, the ownership rules and optimistic-lock handling.
 * Authorisation is decided from the caller id passed in, never from session state.
 */
@ApplicationScoped
public class PetService {

    private PetRepository pets;
    private UserRepository users;
    private CategoryService categories;
    private ImageStore images;

    /** Self-reference, so the scalar overloads reach {@code @Valid} through the CDI proxy. */
    @Inject
    private PetService self;

    /** For CDI only. */
    protected PetService() {
    }

    @Inject
    public PetService(PetRepository pets, UserRepository users, CategoryService categories,
                      ImageStore images) {
        this.pets = pets;
        this.users = users;
        this.categories = categories;
        this.images = images;
    }

    /**
     * Lists the public gallery: available pets only, newest first.
     *
     * @param categoryId category filter, or null for any
     * @param size       size filter, or null for any
     * @param gender     gender filter, or null for any
     * @return the matching pets, never null
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Pet> findGallery(Integer categoryId, Pet.PetSize size, Pet.PetGender gender) {
        return pets.find(categoryId, size, gender, null, true);
    }

    /**
     * Lists every pet for the admin panel, in all statuses.
     *
     * @param categoryId category filter, or null for any
     * @param size       size filter, or null for any
     * @param gender     gender filter, or null for any
     * @return the matching pets, newest first
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Pet> findAllForAdmin(Integer categoryId, Pet.PetSize size, Pet.PetGender gender) {
        return pets.find(categoryId, size, gender, null, false);
    }

    /**
     * Hides or restores a listing.
     *
     * @param petId  the listing to move
     * @param status REMOVED or AVAILABLE, in any case
     * @return the listing in its new state
     * @throws AppException 404 if no such pet, 400 if the status is anything else, 409 if the
     *                      listing changed concurrently
     */
    @Transactional
    public Pet changeStatus(Long petId, String status) {
        Pet pet = requireById(petId);
        pet.setStatus(moderationStatus(status));
        try {
            return pets.save(pet);
        } catch (OptimisticLockException e) {
            throw concurrentEdit(e);
        }
    }

    private static Pet.PetStatus moderationStatus(String status) {
        String value = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if (Pet.PetStatus.REMOVED.name().equals(value)) {
            return Pet.PetStatus.REMOVED;
        }
        if (Pet.PetStatus.AVAILABLE.name().equals(value)) {
            return Pet.PetStatus.AVAILABLE;
        }
        throw new AppException(400, "status must be REMOVED or AVAILABLE");
    }

    /**
     * Loads one pet in full. Callers decide whether to show the owner's contact details.
     *
     * @param id the pet's id
     * @return the pet, with its category and owner loaded
     * @throws AppException 404 if no such pet
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public Pet findDetail(Long id) {
        return requireById(id);
    }

    /**
     * Creates a listing owned by the calling user.
     *
     * @param form        the validated form; it carries no owner field by design
     * @param ownerUserId the session user's id
     * @return the created listing
     * @throws AppException 401 if that user no longer exists, 404 for an unknown category
     */
    @Transactional
    public Pet create(@Valid PetForm form, Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalStateException(
                    "create requires an authenticated caller; the REST tier should have rejected this request");
        }

        User owner = users.findById(ownerUserId).orElseThrow(() -> new AppException(401,
                "Your account is no longer available; please log in again"));

        Pet pet = new Pet();
        applyForm(form, pet);
        pet.setOwner(owner);
        pet.setStatus(Pet.PetStatus.AVAILABLE);

        return pets.save(pet);
    }

    /** Scalar overload for the JSF tier, which cannot bind a record. */
    public Pet create(String name, String breed, Integer age, String size, String gender,
                      String shortDesc, String longDesc, Integer categoryId, Long ownerUserId) {
        return self.create(new PetForm(name, breed, age, size, gender, shortDesc, longDesc, categoryId),
                ownerUserId);
    }

    /**
     * Edits a listing. Only its owner may do so — not an administrator.
     *
     * <p>Optimistic locking works in two layers. The explicit version check catches a stale
     * form: one opened before somebody else saved. {@code @Version} on {@link Pet} catches the
     * narrower race of two saves that both pass the check and reach the database together.
     *
     * @param petId           the pet's id
     * @param form            the validated form
     * @param expectedVersion the version the caller saw when it loaded the listing
     * @param callerUserId    the session user's id
     * @return the updated listing
     * @throws AppException 404 if no such pet, 403 if not the owner, 409 if the listing changed
     *                      since the caller loaded it
     */
    @Transactional
    public Pet update(Long petId, @Valid PetForm form, Long expectedVersion, Long callerUserId) {
        Pet pet = requireById(petId);

        // Ownership first: a non-owner learns nothing about the listing's edit history.
        if (!isSameUser(pet.getOwner(), callerUserId)) {
            throw new AppException(403, "Only the owner of a listing can edit it");
        }

        // Boxed Long values are compared with equals, never with ==, which tests identity.
        if (expectedVersion == null || !expectedVersion.equals(pet.getVersion())) {
            throw concurrentEdit(null);
        }

        applyForm(form, pet);

        try {
            return pets.save(pet);
        } catch (OptimisticLockException e) {
            throw concurrentEdit(e);
        }
    }

    /** Scalar overload for the JSF tier, which cannot bind a record. */
    public Pet update(Long petId, String name, String breed, Integer age, String size,
                      String gender, String shortDesc, String longDesc, Integer categoryId,
                      Long expectedVersion, Long callerUserId) {
        return self.update(petId, new PetForm(name, breed, age, size, gender, shortDesc, longDesc,
                categoryId), expectedVersion, callerUserId);
    }

    /**
     * The one 409 for an edit that lost a race with another edit of the same listing.
     *
     * @param cause the underlying lock failure, or null when the version check caught it first
     * @return the exception to throw
     */
    private static AppException concurrentEdit(Throwable cause) {
        return new AppException(409,
                "This listing was changed by someone else; reload it and try again", cause);
    }

    /**
     * Deletes a listing and its photograph. Owner or administrator only.
     *
     * @param petId         the pet's id
     * @param callerUserId  the session user's id
     * @param callerIsAdmin whether that user is an administrator
     * @throws AppException 404 if no such pet, 403 if neither owner nor admin, 409 if the
     *                      listing changed concurrently
     */
    @Transactional
    public void delete(Long petId, Long callerUserId, boolean callerIsAdmin) {
        Pet pet = requireById(petId);

        if (!callerIsAdmin && !isSameUser(pet.getOwner(), callerUserId)) {
            throw new AppException(403,
                    "Only the owner of a listing, or an administrator, can remove it");
        }

        // Read the filename before the row goes; the cascade takes the row, not the file.
        String photograph = pet.getImageUrl();
        try {
            pets.delete(pet);
        } catch (OptimisticLockException e) {
            throw concurrentEdit(e);
        }
        images.delete(photograph);
    }

    /**
     * Replaces a listing's photograph. The old file is deleted only once the new one is attached.
     *
     * @param petId        the pet's id
     * @param file         the uploaded file
     * @param callerUserId the session user's id
     * @return the pet, with its new photograph
     * @throws AppException 404 if no such pet, 403 if not the owner
     */
    @Transactional
    public Pet attachImage(Long petId, Part file, Long callerUserId) {
        Pet pet = requireById(petId);
        if (!isSameUser(pet.getOwner(), callerUserId)) {
            throw new AppException(403, "Only the owner of a listing can change its photo");
        }
        String previous = pet.getImageUrl();
        pet.setImageUrl(images.store(file));
        Pet saved = pets.save(pet);
        if (previous != null) {
            images.delete(previous);
        }
        return saved;
    }

    /**
     * Lists everything one user has posted, in all statuses, for their dashboard.
     *
     * @param ownerUserId the owner's id, may be null
     * @return that owner's listings, newest first; empty for a null or unknown id
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Pet> findByOwner(Long ownerUserId) {
        if (ownerUserId == null) {
            return List.of();
        }
        return pets.find(null, null, null, ownerUserId, false);
    }

    /**
     * Whether a user owns a listing, so the views can decide what to draw.
     *
     * @param petId  the pet's id, may be null
     * @param userId the user's id, may be null
     * @return true only when both ids are present, the pet exists, and it belongs to that user
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public boolean isOwner(Long petId, Long userId) {
        if (petId == null || userId == null) {
            return false;
        }
        return pets.findById(petId)
                .map(pet -> isSameUser(pet.getOwner(), userId))
                .orElse(false);
    }

    /**
     * Loads a managed pet by id. Package-private, so no entity escapes to the wire this way.
     *
     * @param petId the pet's id, may be null
     * @return the managed pet
     * @throws AppException 404 if no such pet
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    Pet requireById(Long petId) {
        return pets.findById(petId)
                .orElseThrow(() -> new AppException(404, "No such pet: " + petId));
    }

    /** Copies the editable form fields onto a pet, shared by create and update. */
    private void applyForm(PetForm form, Pet pet) {
        pet.setPetName(form.name().trim());
        pet.setBreed(trimToNull(form.breed()));
        pet.setAge(form.age());
        // Safe: @Pattern on PetForm has already rejected anything else.
        pet.setSize(Pet.PetSize.valueOf(form.size()));
        pet.setGender(Pet.PetGender.valueOf(form.gender()));
        pet.setShortDesc(trimToNull(form.shortDesc()));
        pet.setLongDesc(trimToNull(form.longDesc()));
        pet.setCategory(categories.requireById(form.categoryId()));
    }

    /** Identity by id, never by reference: a detached copy and a managed one are the same user. */
    private static boolean isSameUser(User owner, Long userId) {
        return owner != null && userId != null && userId.equals(owner.getUserId());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
