package com.petlee.service;

import com.petlee.dto.AdminPetDTO;
import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetForm;
import com.petlee.exception.ConflictException;
import com.petlee.exception.ForbiddenException;
import com.petlee.exception.NotFoundException;
import com.petlee.exception.UnauthorizedException;
import com.petlee.exception.ValidationException;
import com.petlee.mapper.PetMapper;
import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.User;
import com.petlee.repository.PetRepository;
import com.petlee.repository.UserRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.Part;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listings, ownership rules and concurrency — every business rule in specification §5 and the
 * authorisation logic in §2.
 *
 * <h2>Where authorisation happens</h2>
 * Every decision in this class is made against the <strong>caller id passed in by the REST tier
 * from the session</strong>. This class reads no {@code HttpSession}, no {@code SecurityContext}
 * and no thread-local. That is not a style preference: it is what lets these rules be tested
 * without a container, and what stops the same rule being re-derived in a resource class or a
 * managed bean, where a second copy would eventually disagree with this one.
 *
 * <h2>The privacy rule</h2>
 * {@link #findDetail(Long, Long)} is the <strong>only</strong> place that decides whether owner
 * contact details are visible. Specification §6: "Unregistered clients can view details of pets
 * offered for adoption without seeing private contact information." No resource and no managed
 * bean may re-derive it — a UI check is not a security boundary.
 *
 * <h2>Statuses</h2>
 * Nothing here moves a pet to {@code ADOPTED}. Specification §3 does not ask for it, so it is not
 * invented. {@code REMOVED} exists for T-34's admin soft-hiding; {@link #delete} is a hard delete.
 */
@ApplicationScoped
public class PetService {

    private static final Logger LOGGER = Logger.getLogger(PetService.class.getName());

    private static final int NAME_MAX = 100;
    private static final int BREED_MAX = 100;
    private static final int SHORT_DESC_MAX = 255;
    private static final int AGE_MIN = 0;
    private static final int AGE_MAX = 50;

    private PetRepository pets;
    private UserRepository users;
    private CategoryService categories;

    /**
     * Stores and removes the single photograph a pet may carry. Used both by
     * {@link #attachImage} and, for cleanup, by {@link #delete}.
     */
    private ImageStore images;

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
     * <p>{@code AVAILABLE} only and newest first, both enforced by T-08's query rather than here,
     * so no caller can widen them by passing a different filter. Open to everyone, guests included.
     *
     * @param categoryId the category to restrict to, or {@code null} for any category
     * @param size       the size to restrict to, or {@code null} for any size
     * @param gender     the gender to restrict to, or {@code null} for any gender
     * @return the matching pets in gallery shape; empty when none match, never {@code null}
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<PetDTO> findGallery(Integer categoryId, Pet.PetSize size, Pet.PetGender gender) {
        return pets.find(categoryId, size, gender, null, true).stream().map(PetMapper::toDto).toList();
    }

    /**
     * The moderation listing — {@code GET /api/admin/pets} (T-34).
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
    public List<AdminPetDTO> findAllForAdmin(Integer categoryId, Pet.PetSize size, Pet.PetGender gender) {
        return pets.find(categoryId, size, gender, null, false).stream()
                .map(PetMapper::toAdminDto).toList();
    }

    /**
     * Hides or restores a listing — {@code PUT /api/admin/pets/{id}/status} (T-34).
     *
     * <p>Specification §2 asks an administrator to prevent offensive listings. {@link #delete} is
     * the irreversible answer; this is the recoverable one, so only the two statuses that move a
     * listing out of and back into the public gallery are accepted. {@code ADOPTED} is not: nothing
     * in specification §3 asks for it, and an administrator is not the person who would know.
     *
     * @param petId  the listing to hide or restore
     * @param status {@code REMOVED} or {@code AVAILABLE}, in any case
     * @return the listing in its new state
     * @throws NotFoundException <strong>404</strong> — no pet has that id
     * @throws ValidationException <strong>400</strong> — {@code status} is missing or is any other
     *         value
     */
    @Transactional
    public PetDTO changeStatus(Long petId, String status) {
        Pet pet = requireById(petId);
        pet.setStatus(moderationStatus(status));
        Pet saved = pets.save(pet);

        LOGGER.log(Level.INFO, () -> "Admin set pet " + petId + " to " + saved.getStatus());
        return PetMapper.toDto(saved);
    }

    private static Pet.PetStatus moderationStatus(String status) {
        String value = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if (Pet.PetStatus.REMOVED.name().equals(value)) {
            return Pet.PetStatus.REMOVED;
        }
        if (Pet.PetStatus.AVAILABLE.name().equals(value)) {
            return Pet.PetStatus.AVAILABLE;
        }
        throw new ValidationException("status", "INVALID_STATUS",
                "status must be REMOVED or AVAILABLE");
    }

    /**
     * One pet in full — {@code GET /api/pets/{id}}.
     *
     * <p><strong>This is where the contact-details decision is made, and the only place.</strong>
     * A {@code null} caller is a guest, and the contract is explicit: "Owner contact fields are
     * filled ONLY if the caller is logged in; otherwise null."
     *
     * @param id           the pet's id
     * @param callerUserId the logged-in caller's id, or {@code null} for a guest
     * @return the pet with {@code ownerFullName}, {@code ownerEmail} and {@code ownerPhone}
     *         populated for a logged-in caller and {@code null} for a guest
     * @throws NotFoundException <strong>404</strong> — no pet has that id
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public PetDetailDTO findDetail(Long id, Long callerUserId) {
        Pet pet = requireById(id);
        return PetMapper.toDetailDto(pet, callerUserId != null);
    }

    /**
     * Creates a listing — {@code POST /api/pets}.
     *
     * <p>The owner is taken from {@code ownerUserId}, which the REST tier reads from the session.
     * {@link PetForm} has no owner field and must not gain one: specification §5 says "Each pet
     * listing has one, and only one, owner", and an owner that can be named in the request body is
     * an owner an attacker can choose.
     *
     * @param form        the request body
     * @param ownerUserId the session user's id; must not be {@code null}
     * @return the created listing
     * @throws IllegalStateException if {@code ownerUserId} is {@code null}. Not a 401: T-18 has
     *         already rejected anonymous callers before this method is reachable, so a null here
     *         means a resource method is missing its {@code @Secured} annotation — a bug to fix,
     *         not a client error to report.
     * @throws UnauthorizedException <strong>401</strong> if the session user no longer exists. That
     *         one <em>is</em> a client condition — the account was deleted while the session lived
     *         — and the honest answer is "log in again".
     * @throws ValidationException <strong>400</strong> — a field is missing, too long, out of
     *         range, or not one of the contract's enum strings; {@code getField()} names which
     * @throws NotFoundException <strong>404</strong> — unknown {@code categoryId}; specification
     *         §5, "Every posted pet must belong to a predefined category"
     */
    @Transactional
    public PetDTO create(PetForm form, Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalStateException(
                    "create requires an authenticated caller; T-18 should have rejected this request");
        }
        if (form == null) {
            throw new ValidationException("EMPTY_BODY", "A pet body is required");
        }

        User owner = users.findById(ownerUserId).orElseThrow(() -> new UnauthorizedException(
                "SESSION_USER_GONE", "Your account is no longer available; please log in again"));

        Pet pet = new Pet();
        applyForm(form, pet);
        pet.setOwner(owner);
        pet.setStatus(Pet.PetStatus.AVAILABLE);

        Pet saved = pets.save(pet);
        LOGGER.log(Level.INFO, () -> "User " + ownerUserId + " created pet " + saved.getPetId());
        return PetMapper.toDto(saved);
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
     * @param form         the request body
     * @param callerUserId the session user's id
     * @return the updated listing
     * @throws NotFoundException <strong>404</strong> — no pet has that id
     * @throws ForbiddenException <strong>403</strong> — {@code api-contract.md}: "403 if not the
     *         owner"
     * @throws ValidationException <strong>400</strong> — as {@link #create}
     * @throws ConflictException <strong>409</strong>, code {@code STALE_PET} — {@code
     *         api-contract.md}: "409 if a concurrent edit happened (optimistic lock)". This is
     *         specification §4's concurrency control — "prevent situations where two users update
     *         the same pet listing simultaneously and overwrite data" — made visible to the client.
     */
    @Transactional
    public PetDTO update(Long petId, PetForm form, Long callerUserId) {
        Pet pet = requireById(petId);

        if (!isSameUser(pet.getOwner(), callerUserId)) {
            throw new ForbiddenException("NOT_OWNER", "Only the owner of a listing can edit it");
        }
        if (form == null) {
            throw new ValidationException("EMPTY_BODY", "A pet body is required");
        }

        applyForm(form, pet);

        try {
            Pet saved = pets.save(pet);
            return PetMapper.toDto(saved);
        } catch (OptimisticLockException e) {
            // @Version caught a concurrent edit. PetRepository.save flushes, so it arrives
            // here rather than at commit, where the transaction manager would have wrapped it.
            LOGGER.log(Level.FINE, e, () -> "Stale update of pet " + petId + " by user " + callerUserId);
            throw new ConflictException("STALE_PET",
                    "This listing was changed by someone else; reload it and try again", e);
        }
    }

    /**
     * Removes a listing — {@code DELETE /api/pets/{id}}.
     *
     * <p>Specification §5: "Only the original poster of a pet listing can remove it (or an
     * administrator)." A <strong>hard</strong> delete; the images cascade with the row (T-03,
     * T-09). {@code REMOVED} is T-34's soft-hide and is not used here.
     *
     * @param petId         the pet's id
     * @param callerUserId  the session user's id
     * @param callerIsAdmin whether that user's role is {@code ADMIN}, decided by the REST tier from
     *                      the session — never read from the request
     * @throws NotFoundException <strong>404</strong> — no pet has that id
     * @throws ForbiddenException <strong>403</strong> — {@code api-contract.md}: "403 if not owner
     *         and not admin"
     */
    @Transactional
    public void delete(Long petId, Long callerUserId, boolean callerIsAdmin) {
        Pet pet = requireById(petId);

        if (!callerIsAdmin && !isSameUser(pet.getOwner(), callerUserId)) {
            throw new ForbiddenException("NOT_OWNER_OR_ADMIN",
                    "Only the owner of a listing, or an administrator, can remove it");
        }

        // Read before the row goes, because after it the pet is gone with it.
        String photograph = pet.getImageUrl();

        pets.delete(pet);

        // The database cascade takes the row; nothing took the file. Deleting a withdrawn
        // listing has to mean its photograph is gone, not merely unlisted.
        images.delete(photograph);

        LOGGER.log(Level.INFO, () -> "User " + callerUserId + (callerIsAdmin ? " (admin)" : "")
                + " deleted pet " + petId);
    }

    /**
     * Replaces a listing's photograph — {@code POST /api/pets/{id}/image}.
     *
     * <p>Deviates from {@code api-contract.md}'s {@code POST /api/pets/{id}/images}: a pet now
     * carries one photograph, not a gallery, so there is nothing left to be "main" among. The
     * previous file, if there was one, is deleted only after the new one is safely attached — so
     * a failed upload never loses a listing's existing photograph.
     *
     * @param petId        the pet's id
     * @param file         the uploaded file
     * @param callerUserId the session user's id
     * @return the pet, with its new photograph
     * @throws NotFoundException  <strong>404</strong> — no pet has that id
     * @throws AppException <strong>403</strong> — only the owner of a listing may change its photo
     */
    @Transactional
    public PetDTO attachImage(Long petId, Part file, Long callerUserId) {
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
        return PetMapper.toDto(saved);
    }

    /**
     * Everything one user has ever listed — the T-32 dashboard feed.
     *
     * <p><strong>All statuses</strong>, {@code REMOVED} included: an owner has to see the listings
     * that left the gallery, or a disappeared advert looks like data loss.
     *
     * @param ownerUserId the owner's id, may be {@code null}
     * @return that owner's listings, newest first; empty for a {@code null} or unknown id
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<PetDTO> findByOwner(Long ownerUserId) {
        if (ownerUserId == null) {
            return List.of();
        }
        return pets.find(null, null, null, ownerUserId, false).stream().map(PetMapper::toDto).toList();
    }

    /**
     * Whether a user owns a listing — so the JSF tier can decide whether to draw an Edit button,
     * and T-16 can authorise an upload, without either of them re-deriving the rule.
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
     * authorising the caller — routing it through this method keeps the 404 rule from being
     * duplicated in {@link PetRepository}.
     *
     * <p>Like {@code CategoryService.requireById}, it is <strong>package-private</strong>, and the
     * visibility is the enforcement: REST resources are not in {@code com.petlee.service}, so no
     * entity can escape to the wire through this door.
     *
     * @param petId the pet's id, may be {@code null}
     * @return the managed pet
     * @throws NotFoundException <strong>404</strong>, code {@code PET_NOT_FOUND}
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    Pet requireById(Long petId) {
        return pets.findById(petId)
                .orElseThrow(() -> new NotFoundException("PET_NOT_FOUND", "No such pet: " + petId));
    }

    /**
     * Copies the editable fields of a form onto a pet, validating as it goes.
     *
     * <p>Shared by {@link #create} and {@link #update} so the two cannot validate differently —
     * a listing that could be created but not re-saved unchanged would be the result.
     */
    private void applyForm(PetForm form, Pet pet) {
        String name = trimToNull(form.getName());
        if (name == null || name.length() > NAME_MAX) {
            throw new ValidationException("name", "NAME_INVALID",
                    "A name is required, of at most " + NAME_MAX + " characters");
        }

        String breed = trimToNull(form.getBreed());
        // Not in the task file's list, but breed is VARCHAR(100): without this bound an over-long
        // value fails at the INSERT as a 500 the caller cannot act on. Same reasoning as T-13's
        // phone bound.
        if (breed != null && breed.length() > BREED_MAX) {
            throw new ValidationException("breed", "BREED_TOO_LONG",
                    "Breed must be at most " + BREED_MAX + " characters");
        }

        Integer age = form.getAge();
        if (age != null && (age < AGE_MIN || age > AGE_MAX)) {
            throw new ValidationException("age", "AGE_OUT_OF_RANGE",
                    "Age must be between " + AGE_MIN + " and " + AGE_MAX + " years");
        }

        String shortDesc = trimToNull(form.getShortDesc());
        if (shortDesc != null && shortDesc.length() > SHORT_DESC_MAX) {
            throw new ValidationException("shortDesc", "SHORT_DESC_TOO_LONG",
                    "The short description must be at most " + SHORT_DESC_MAX + " characters");
        }

        Pet.PetSize size = parseEnum(Pet.PetSize.class, form.getSize(), "size",
                "SMALL, MEDIUM or LARGE");
        Pet.PetGender gender = parseEnum(Pet.PetGender.class, form.getGender(), "gender",
                "MALE or FEMALE");

        if (form.getCategoryId() == null) {
            throw new ValidationException("categoryId", "CATEGORY_REQUIRED",
                    "A category is required");
        }
        // Specification §5: every posted pet must belong to a predefined category. Resolving it
        // through CategoryService means an unknown id is a 404 here, not a foreign-key 500 later.
        Category category = categories.requireById(form.getCategoryId());

        pet.setPetName(name);
        pet.setBreed(breed);
        pet.setAge(age);
        pet.setSize(size);
        pet.setGender(gender);
        pet.setShortDesc(shortDesc);
        pet.setLongDesc(trimToNull(form.getLongDesc()));
        pet.setCategory(category);
    }

    /**
     * Parses one of the contract's enum strings.
     *
     * <p>Trimmed and upper-cased first, so {@code "medium"} is accepted: the contract fixes the
     * strings the server <em>emits</em>, and being lenient about what it accepts costs nothing and
     * cannot be ambiguous. Anything else is a 400 naming the field and listing the legal values,
     * rather than a deserialisation failure the caller cannot read — which is why {@link PetForm}
     * holds these as {@code String}.
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field,
                                                   String legalValues) {
        String value = trimToNull(raw);
        if (value != null) {
            try {
                return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall through to the one throw site below, so the message is written once.
            }
        }
        throw new ValidationException(field, field.toUpperCase(Locale.ROOT) + "_INVALID",
                "The " + field + " must be one of: " + legalValues);
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
