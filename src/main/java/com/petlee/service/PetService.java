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
 * Listings, ownership rules and concurrency — every business rule in specification §5 and the
 * authorisation logic in §2.
 *
 * <h2>Where authorisation happens</h2>
 * Every decision in this class is made against the <strong>caller id passed in by the REST tier
 * from the session</strong>. This class reads no {@code HttpSession}, no {@code SecurityContext}
 * and no thread-local. That is not a style preference: it is what lets these rules be exercised
 * without a container, and what stops the same rule being re-derived in a resource class or a
 * managed bean, where a second copy would eventually disagree with this one.
 *
 * <h2>Where validation happens now</h2>
 * {@link #create(PetForm, Long)} and {@link #update(Long, PetForm, Long)} take a {@code @Valid}
 * form, enforced by the container's Bean Validation provider through a CDI interceptor bound to
 * this {@code @ApplicationScoped} bean. The field-length, range and pattern checks that used to
 * be hand-written here now live as annotations on {@link PetForm}. The scalar overloads used by
 * the JSF tier delegate through the injected {@link #self} reference, not {@code this} — a
 * same-instance call would bypass the proxy the interceptor is woven onto, and validation would
 * silently stop running for every form submission.
 *
 * <h2>The privacy rule</h2>
 * {@link #findDetail(Long)} returns the full entity, owner attached; specification §6 —
 * "Unregistered clients can view details of pets offered for adoption without seeing private
 * contact information" — is enforced by each caller at its own boundary: {@code PetDetailDTO.of}
 * on the REST side, {@code PetDetailBean.isContactVisible()} on the JSF side. Both must gate; the
 * entity itself carries the owner unconditionally.
 *
 * <h2>Statuses</h2>
 * Nothing here moves a pet to {@code ADOPTED}. Specification §3 does not ask for it, so it is not
 * invented. {@code REMOVED} exists for the admin's soft-hiding; {@link #delete} is a hard delete.
 */
@ApplicationScoped
public class PetService {

    private PetRepository pets;
    private UserRepository users;
    private CategoryService categories;

    /**
     * Stores and removes the single photograph a pet may carry. Used both by
     * {@link #attachImage} and, for cleanup, by {@link #delete}.
     */
    private ImageStore images;

    /**
     * A self-reference, injected so the scalar overloads can call the {@code @Valid}-annotated
     * methods through the proxy instead of through {@code this}. See the class documentation.
     */
    @Inject
    private PetService self;

    /** For CDI only — an {@code @ApplicationScoped} proxy needs a no-argument constructor. */
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
     * The public catalogue — {@code GET /api/pets}.
     *
     * <p>{@code AVAILABLE} only and newest first, both enforced by the repository's query rather
     * than here, so no caller can widen them by passing a different filter. Open to everyone,
     * guests included.
     *
     * @param categoryId the category to restrict to, or {@code null} for any category
     * @param size       the size to restrict to, or {@code null} for any size
     * @param gender     the gender to restrict to, or {@code null} for any gender
     * @return the matching pets in gallery shape; empty when none match, never {@code null}
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Pet> findGallery(Integer categoryId, Pet.PetSize size, Pet.PetGender gender) {
        return pets.find(categoryId, size, gender, null, true);
    }

    /**
     * The moderation listing — {@code GET /api/admin/pets}.
     *
     * <p>The same filters as the gallery, without the status restriction: {@code ADOPTED} and
     * {@code REMOVED} listings are included, which is the whole point of the endpoint. Nothing
     * here checks the caller's role — {@code @AdminOnly} on the resource has already done it, and
     * a second check that could disagree is worse than none.
     *
     * @param categoryId the category to restrict to, or {@code null} for any category
     * @param size       the size to restrict to, or {@code null} for any size
     * @param gender     the gender to restrict to, or {@code null} for any gender
     * @return every matching listing, newest first, in every status
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Pet> findAllForAdmin(Integer categoryId, Pet.PetSize size, Pet.PetGender gender) {
        return pets.find(categoryId, size, gender, null, false);
    }

    /**
     * Hides or restores a listing — {@code PUT /api/admin/pets/{id}/status}.
     *
     * <p>Specification §2 asks an administrator to prevent offensive listings. {@link #delete} is
     * the irreversible answer; this is the recoverable one, so only the two statuses that move a
     * listing out of and back into the public gallery are accepted. {@code ADOPTED} is not: nothing
     * in specification §3 asks for it, and an administrator is not the person who would know.
     *
     * @param petId  the listing to hide or restore
     * @param status {@code REMOVED} or {@code AVAILABLE}, in any case
     * @return the listing in its new state
     * @throws AppException <strong>404</strong> — no pet has that id
     * @throws AppException <strong>400</strong> — {@code status} is missing or is any other value
     */
    @Transactional
    public Pet changeStatus(Long petId, String status) {
        Pet pet = requireById(petId);
        pet.setStatus(moderationStatus(status));
        return pets.save(pet);
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
     * One pet in full, owner attached — {@code GET /api/pets/{id}}.
     *
     * <p>Whether the owner's contact details may be shown is no longer decided here: the caller
     * — {@code PetResource} for REST, {@code PetDetailBean} for JSF — gates it at its own
     * boundary. Specification §6's rule still applies; it is just enforced twice now, once per
     * tier, because a record and an entity cannot share one gating method.
     *
     * @param id the pet's id
     * @return the managed pet, with its category and owner loaded
     * @throws AppException <strong>404</strong> — no pet has that id
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public Pet findDetail(Long id) {
        return requireById(id);
    }

    /**
     * Creates a listing — {@code POST /api/pets}.
     *
     * <p>The owner is taken from {@code ownerUserId}, which the REST tier reads from the session.
     * {@link PetForm} has no owner field and must not gain one: specification §5 says "Each pet
     * listing has one, and only one, owner", and an owner that can be named in the request body is
     * an owner an attacker can choose.
     *
     * @param form        the request body; validated by the container before this body runs
     * @param ownerUserId the session user's id; must not be {@code null}
     * @return the created listing
     * @throws IllegalStateException if {@code ownerUserId} is {@code null}. Not a 401: the REST
     *         tier has already rejected anonymous callers before this method is reachable, so a
     *         null here means a resource method is missing its {@code @Secured} annotation — a
     *         bug to fix, not a client error to report.
     * @throws AppException <strong>401</strong> if the session user no longer exists. That one
     *         <em>is</em> a client condition — the account was deleted while the session lived —
     *         and the honest answer is "log in again".
     * @throws jakarta.validation.ConstraintViolationException a field is missing, too long, out
     *         of range, or not one of the contract's enum strings
     * @throws AppException <strong>404</strong> — unknown {@code categoryId}; specification §5,
     *         "Every posted pet must belong to a predefined category"
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

    /**
     * The JSF tier's entry point into {@link #create(PetForm, Long)}.
     *
     * <p>The web tier's package may not import the DTO package at all — a record cannot be
     * bound into a Facelets view, so nothing in that package should have a reason to reach for
     * one. {@code PetFormBean} holds its fields individually instead and calls this overload,
     * which is the one place a {@link PetForm} is assembled from them before delegating — through
     * {@link #self}, not {@code this} — to the canonical, validated method that
     * {@link com.petlee.rest.PetResource} calls directly with the record JSON-B already built.
     *
     * @see #create(PetForm, Long)
     */
    public Pet create(String name, String breed, Integer age, String size, String gender,
                      String shortDesc, String longDesc, Integer categoryId, Long ownerUserId) {
        return self.create(new PetForm(name, breed, age, size, gender, shortDesc, longDesc, categoryId),
                ownerUserId);
    }

    /**
     * Edits a listing — {@code PUT /api/pets/{id}}.
     *
     * <p><strong>Only the owner may edit.</strong> An administrator may not, even though an
     * administrator may delete: the contract grants admins delete rights only, and rewriting
     * someone else's advert is a different act from removing one that breaks a policy.
     *
     * <p>{@code owner}, {@code createdAt} and {@code status} are not editable through this path,
     * and {@link PetForm} carries none of them, so the "silently ignore any attempt" rule is
     * satisfied by construction rather than by a filter that could be forgotten.
     *
     * @param petId        the pet's id
     * @param form         the request body; validated by the container before this body runs
     * @param callerUserId the session user's id
     * @return the updated listing
     * @throws AppException <strong>404</strong> — no pet has that id
     * @throws AppException <strong>403</strong> — {@code api-contract.md}: "403 if not the owner"
     * @throws jakarta.validation.ConstraintViolationException as {@link #create}
     * @throws AppException <strong>409</strong> — {@code api-contract.md}: "409 if a concurrent
     *         edit happened (optimistic lock)". This is specification §4's concurrency control —
     *         "prevent situations where two users update the same pet listing simultaneously and
     *         overwrite data" — made visible to the client.
     */
    @Transactional
    public Pet update(Long petId, @Valid PetForm form, Long callerUserId) {
        Pet pet = requireById(petId);

        if (!isSameUser(pet.getOwner(), callerUserId)) {
            throw new AppException(403, "Only the owner of a listing can edit it");
        }

        applyForm(form, pet);

        try {
            return pets.save(pet);
        } catch (OptimisticLockException e) {
            // @Version caught a concurrent edit. save() flushes, so it arrives here rather than
            // at commit, where the transaction manager would have wrapped it.
            throw new AppException(409,
                    "This listing was changed by someone else; reload it and try again", e);
        }
    }

    /**
     * The JSF tier's entry point into {@link #update(Long, PetForm, Long)}. See
     * {@link #create(String, String, Integer, String, String, String, String, Integer, Long)} for
     * why this overload exists and why it delegates through {@link #self}.
     *
     * @see #update(Long, PetForm, Long)
     */
    public Pet update(Long petId, String name, String breed, Integer age, String size,
                      String gender, String shortDesc, String longDesc, Integer categoryId,
                      Long callerUserId) {
        return self.update(petId, new PetForm(name, breed, age, size, gender, shortDesc, longDesc,
                categoryId), callerUserId);
    }

    /**
     * Removes a listing — {@code DELETE /api/pets/{id}}.
     *
     * <p>Specification §5: "Only the original poster of a pet listing can remove it (or an
     * administrator)." A <strong>hard</strong> delete; the photograph cascades with the row.
     * {@code REMOVED} is the admin's soft-hide and is not used here.
     *
     * @param petId         the pet's id
     * @param callerUserId  the session user's id
     * @param callerIsAdmin whether that user's role is {@code ADMIN}, decided by the REST tier from
     *                      the session — never read from the request
     * @throws AppException <strong>404</strong> — no pet has that id
     * @throws AppException <strong>403</strong> — {@code api-contract.md}: "403 if not owner and
     *         not admin"
     */
    @Transactional
    public void delete(Long petId, Long callerUserId, boolean callerIsAdmin) {
        Pet pet = requireById(petId);

        if (!callerIsAdmin && !isSameUser(pet.getOwner(), callerUserId)) {
            throw new AppException(403,
                    "Only the owner of a listing, or an administrator, can remove it");
        }

        // Read before the row goes, because after it the pet is gone with it.
        String photograph = pet.getImageUrl();

        pets.delete(pet);

        // The database cascade takes the row; nothing took the file. Deleting a withdrawn
        // listing has to mean its photograph is gone, not merely unlisted.
        images.delete(photograph);
    }

    /**
     * Replaces a listing's photograph — reachable only from the JSF form, via
     * {@code PetFormBean.save()}. There is no REST endpoint for this any more: the equivalent
     * {@code POST /api/pets/{id}/image} was removed entirely (ADR-002 #10) because Jersey cannot
     * inject a Servlet {@code Part} as a {@code @FormParam}.
     *
     * <p>A pet carries one photograph, not a gallery. The previous file, if there was one, is
     * deleted only after the new one is safely attached — so a failed upload never loses a
     * listing's existing photograph.
     *
     * @param petId        the pet's id
     * @param file         the uploaded file
     * @param callerUserId the session user's id
     * @return the pet, with its new photograph
     * @throws AppException <strong>404</strong> — no pet has that id
     * @throws AppException <strong>403</strong> — only the owner of a listing may change its photo
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
     * Everything one user has ever listed — the dashboard feed.
     *
     * <p><strong>All statuses</strong>, {@code REMOVED} included: an owner has to see the listings
     * that left the gallery, or a disappeared advert looks like data loss.
     *
     * @param ownerUserId the owner's id, may be {@code null}
     * @return that owner's listings, newest first; empty for a {@code null} or unknown id
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Pet> findByOwner(Long ownerUserId) {
        if (ownerUserId == null) {
            return List.of();
        }
        return pets.find(null, null, null, ownerUserId, false);
    }

    /**
     * Whether a user owns a listing — so the JSF tier can decide whether to draw an Edit button,
     * without re-deriving the rule.
     *
     * <p>A UI that hides a button is a convenience, not a boundary: {@link #update} and
     * {@link #delete} check again regardless of what the caller was shown.
     *
     * @param petId  the pet's id, may be {@code null}
     * @param userId the user's id, may be {@code null}
     * @return {@code true} only when both ids are present, the pet exists, and it belongs to that
     *         user. An unknown pet is {@code false}, not an exception — the question was "does
     *         this user own it", and the answer is no.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public boolean isOwner(Long petId, Long userId) {
        if (petId == null || userId == null) {
            return false;
        }
        // findById fetch-joins the owner, so this works under TxType.SUPPORTS, where a lazy
        // owner proxy on a detached pet would not.
        return pets.findById(petId)
                .map(pet -> isSameUser(pet.getOwner(), userId))
                .orElse(false);
    }

    /**
     * The pet as a <strong>managed entity</strong>, with its category and owner loaded, or a 404.
     *
     * <p>Every method here that names a pet by id goes through it, so "unknown id is a 404" is
     * written once. {@link #attachImage} also calls it, to set the entity's photograph after
     * authorising the caller.
     *
     * <p>Like {@code CategoryService.requireById}, it is <strong>package-private</strong>, and the
     * visibility is the enforcement: REST resources are not in {@code com.petlee.service}, so no
     * entity can escape to the wire through this door.
     *
     * @param petId the pet's id, may be {@code null}
     * @return the managed pet
     * @throws AppException <strong>404</strong>
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    Pet requireById(Long petId) {
        return pets.findById(petId)
                .orElseThrow(() -> new AppException(404, "No such pet: " + petId));
    }

    /**
     * Copies the editable fields of a form onto a pet.
     *
     * <p>Shared by {@link #create} and {@link #update} so the two cannot disagree. The field
     * bounds — name length, breed length, age range, short-description length — are enforced
     * before this method runs, by {@code @Valid} on the caller and the constraints on
     * {@link PetForm} itself; only what those annotations cannot express is left here: parsing the
     * enum strings and resolving the category.
     */
    private void applyForm(PetForm form, Pet pet) {
        pet.setPetName(form.name().trim());
        pet.setBreed(trimToNull(form.breed()));
        pet.setAge(form.age());
        // valueOf is safe here because @Pattern(regexp = "SMALL|MEDIUM|LARGE"/"MALE|FEMALE") has
        // already rejected anything else.
        pet.setSize(Pet.PetSize.valueOf(form.size()));
        pet.setGender(Pet.PetGender.valueOf(form.gender()));
        pet.setShortDesc(trimToNull(form.shortDesc()));
        pet.setLongDesc(trimToNull(form.longDesc()));
        // Specification §5: every posted pet must belong to a predefined category. Resolving it
        // through CategoryService means an unknown id is a 404 here, not a foreign-key 500 later.
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
