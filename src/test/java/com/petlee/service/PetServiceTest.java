package com.petlee.service;

import com.petlee.test.Fakes;
import com.petlee.dto.AdminPetDTO;
import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetForm;
import com.petlee.exception.ConflictException;
import com.petlee.exception.ForbiddenException;
import com.petlee.exception.NotFoundException;
import com.petlee.exception.ValidationException;
import com.petlee.model.Pet;
import com.petlee.model.PetImage;
import com.petlee.model.User;
import com.petlee.repository.PetFilter;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T-15's nine acceptance criteria — specification §5's rules and §4's concurrency control. */
class PetServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long STRANGER_ID = 2L;
    private static final Long ADMIN_ID = 3L;

    private Fakes.Pets pets;
    private Fakes.Users users;
    private Fakes.Categories categories;
    private PetService service;

    /** Where a listing's photographs live, so the delete path has real files to remove. */
    @TempDir
    Path uploadRoot;

    private User owner;

    @BeforeEach
    void setUp() {
        pets = new Fakes.Pets();
        users = new Fakes.Users();
        categories = new Fakes.Categories();
        service = new PetService(pets, users, new CategoryService(categories),
                new ImageStorageServiceTest.FixedRoot(uploadRoot));

        owner = user(OWNER_ID, "roy", "Roy Stein", "roy@example.com", "050-1234567");
        user(STRANGER_ID, "stranger", "A Stranger", "stranger@example.com", "050-7654321");
        user(ADMIN_ID, "admin", "The Admin", "admin@example.com", null);
    }

    /**
     * The defect T-32 criterion 6 went looking for. The database cascade takes the {@code
     * pet_image} rows; nothing took the files, so every deleted listing left its photographs on
     * disk — and {@code ImageServlet} went on serving them by public URL to anyone who had seen
     * one. A withdrawn listing whose photographs are still retrievable is not withdrawn.
     */
    @Test
    @DisplayName("deleting a listing deletes its photographs from disk, not just its rows")
    void deleteRemovesTheFilesToo() throws IOException {
        Pet pet = persistedPetWithImages("185_11111111-1111-1111-1111-111111111111.jpg",
                "185_22222222-2222-2222-2222-222222222222.png");
        Path first = uploadRoot.resolve("185_11111111-1111-1111-1111-111111111111.jpg");
        Path second = uploadRoot.resolve("185_22222222-2222-2222-2222-222222222222.png");
        assertTrue(Files.exists(first) && Files.exists(second), "the fixture should have written both");

        service.delete(pet.getPetId(), OWNER_ID, false);

        assertFalse(Files.exists(first), "the main photograph should be gone");
        assertFalse(Files.exists(second), "and so should the other one");
    }

    @Test
    @DisplayName("a listing with no photographs deletes without complaint")
    void deleteWithNoImages() throws IOException {
        Pet pet = persistedPetWithImages();

        service.delete(pet.getPetId(), OWNER_ID, false);

        assertTrue(pets.rows.stream().noneMatch(p -> p.getPetId().equals(pet.getPetId())));
    }

    /**
     * A row whose {@code image_url} is not a shape this application writes must not send the
     * delete anywhere near the filesystem. The column is data, and data that becomes a path
     * deserves the same suspicion as a request parameter.
     */
    @Test
    @DisplayName("a hostile image_url is refused rather than resolved")
    void deleteIgnoresAnUnexpectedImageUrl() throws IOException {
        Path outside = uploadRoot.resolve("keep-me.txt");
        Files.writeString(outside, "not a photograph");
        Pet pet = persistedPetWithImages("/../keep-me.txt", "../keep-me.txt", "keep-me.txt");

        service.delete(pet.getPetId(), OWNER_ID, false);

        assertTrue(Files.exists(outside), "nothing outside the naming convention may be deleted");
    }

    /**
     * Builds a persisted pet whose photographs exist both as rows and as files.
     *
     * @param fileNames the stored names, as T-16 generates them
     * @return the pet
     */
    private Pet persistedPetWithImages(String... fileNames) throws IOException {
        PetForm form = new PetForm();
        form.setName("Rex");
        form.setSize("MEDIUM");
        form.setGender("MALE");
        form.setShortDesc("Friendly");
        form.setCategoryId(1);
        PetDTO created = service.create(form, OWNER_ID);

        Pet pet = pets.rows.stream()
                .filter(p -> p.getPetId().equals(created.getId()))
                .findFirst()
                .orElseThrow();

        for (String fileName : fileNames) {
            if (!fileName.contains("/")) {
                Files.write(uploadRoot.resolve(fileName), new byte[]{1, 2, 3});
            }
            PetImage image = new PetImage();
            image.setImageUrl("/images/" + fileName);
            image.setPet(pet);
            pet.getImages().add(image);
        }
        return pet;
    }

    private User user(Long id, String username, String fullName, String email, String phone) {
        User u = new User();
        u.setUserId(id);
        u.setUserName(username);
        u.setFullName(fullName);
        u.setEmail(email);
        u.setPhoneNumber(phone);
        u.setRole(User.Role.USER);
        users.saved.add(u);
        return u;
    }

    /** The contract's POST /api/pets example body. */
    private static PetForm contractExample() {
        PetForm form = new PetForm();
        form.setName("Rex");
        form.setBreed("Jack Russell");
        form.setAge(3);
        form.setSize("MEDIUM");
        form.setGender("MALE");
        form.setShortDesc("Friendly and energetic");
        form.setLongDesc("Full description here...");
        form.setCategoryId(1);
        return form;
    }

    private Pet createPet(Long ownerId) {
        Long id = service.create(contractExample(), ownerId).getId();
        return pets.findDetailById(id).orElseThrow();
    }

    // ---------------------------------------------------------------- criterion 1

    @Test
    @DisplayName("criterion 1: the gallery is AVAILABLE only, newest first")
    void galleryShowsOnlyAvailablePetsNewestFirst() {
        Pet first = createPet(OWNER_ID);
        Pet adopted = createPet(OWNER_ID);
        Pet removed = createPet(OWNER_ID);
        Pet newest = createPet(OWNER_ID);
        adopted.setStatus(Pet.PetStatus.ADOPTED);
        removed.setStatus(Pet.PetStatus.REMOVED);

        List<PetDTO> gallery = service.findGallery(PetFilter.none());

        assertEquals(List.of(newest.getPetId(), first.getPetId()),
                gallery.stream().map(PetDTO::getId).toList());

        // The restriction lives in T-08's query. What this class is responsible for is calling
        // the AVAILABLE-only method rather than the administration one.
        assertEquals(1, pets.findByFilterCalls);
        assertEquals(0, pets.findAllForAdminCalls, "the public gallery must never use findAllForAdmin");
    }

    @Test
    @DisplayName("the gallery passes the contract's three filter criteria straight through")
    void galleryHonoursTheFilter() {
        createPet(OWNER_ID);

        PetForm cat = contractExample();
        cat.setCategoryId(2);
        cat.setSize("SMALL");
        service.create(cat, OWNER_ID);

        List<PetDTO> small = service.findGallery(PetFilter.builder().size(Pet.PetSize.SMALL).build());

        assertEquals(1, small.size());
        assertEquals("Cats", small.get(0).getCategoryName());
    }

    // ---------------------------------------------------------------- criterion 2

    @Test
    @DisplayName("criterion 2: a guest sees no owner contact details")
    void guestSeesNoContactDetails() {
        Pet pet = createPet(OWNER_ID);

        PetDetailDTO detail = service.findDetail(pet.getPetId(), null);

        assertNull(detail.getOwnerFullName(), "specification §6: a guest must not see the owner's name");
        assertNull(detail.getOwnerEmail(), "specification §6: a guest must not see the owner's email");
        assertNull(detail.getOwnerPhone(), "specification §6: a guest must not see the owner's phone");
        // Everything else is public.
        assertEquals("Rex", detail.getName());
        assertEquals("Dogs", detail.getCategoryName());
    }

    @Test
    @DisplayName("criterion 2: a logged-in caller sees all three contact fields")
    void loggedInCallerSeesContactDetails() {
        Pet pet = createPet(OWNER_ID);

        PetDetailDTO detail = service.findDetail(pet.getPetId(), STRANGER_ID);

        assertEquals("Roy Stein", detail.getOwnerFullName());
        assertEquals("roy@example.com", detail.getOwnerEmail());
        assertEquals("050-1234567", detail.getOwnerPhone());
    }

    @Test
    @DisplayName("an unknown pet is a 404, for guest and caller alike")
    void unknownPetIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.findDetail(999L, null));
        assertThrows(NotFoundException.class, () -> service.findDetail(999L, OWNER_ID));
        assertThrows(NotFoundException.class, () -> service.findDetail(null, OWNER_ID));
    }

    // ---------------------------------------------------------------- criterion 3

    @Test
    @DisplayName("criterion 3: an unknown categoryId is a 404, not a foreign-key error")
    void unknownCategoryIsNotFound() {
        PetForm form = contractExample();
        form.setCategoryId(999);

        NotFoundException e = assertThrows(NotFoundException.class, () -> service.create(form, OWNER_ID));
        assertEquals("CATEGORY_NOT_FOUND", e.getCode());
        assertTrue(pets.rows.isEmpty(), "nothing may be stored when the category is unknown");
    }

    @Test
    @DisplayName("a missing categoryId is a 400 naming the field")
    void missingCategoryIsAValidationFailure() {
        PetForm form = contractExample();
        form.setCategoryId(null);

        ValidationException e = assertThrows(ValidationException.class, () -> service.create(form, OWNER_ID));
        assertEquals("categoryId", e.getField());
    }

    // ---------------------------------------------------------------- criterion 4

    @Test
    @DisplayName("criterion 4: the owner comes from the session, and cannot be named in the body")
    void ownerComesFromTheSessionOnly() {
        // PetForm has no owner property, so an injected "ownerId" in the JSON has nowhere to bind.
        assertFalse(Arrays.stream(PetForm.class.getDeclaredFields())
                        .map(Field::getName)
                        .anyMatch(n -> n.toLowerCase().contains("owner")),
                "PetForm must have no owner field");

        service.create(contractExample(), OWNER_ID);

        assertEquals(OWNER_ID, pets.last().getOwner().getUserId());
        assertEquals(Pet.PetStatus.AVAILABLE, pets.last().getStatus());
    }

    @Test
    @DisplayName("a null caller is a bug in the filter, not a 401")
    void aNullCallerIsAProgrammingError() {
        assertThrows(IllegalStateException.class, () -> service.create(contractExample(), null));
    }

    @Test
    @DisplayName("size and gender must be the contract's enum strings")
    void rejectsUnknownEnumValues() {
        PetForm badSize = contractExample();
        badSize.setSize("ENORMOUS");
        assertEquals("size", assertThrows(ValidationException.class,
                () -> service.create(badSize, OWNER_ID)).getField());

        PetForm noGender = contractExample();
        noGender.setGender(null);
        assertEquals("gender", assertThrows(ValidationException.class,
                () -> service.create(noGender, OWNER_ID)).getField());

        // Lenient about case on the way in; the contract fixes what the server emits.
        PetForm lowerCase = contractExample();
        lowerCase.setSize("medium");
        assertEquals("MEDIUM", service.create(lowerCase, OWNER_ID).getSize());
    }

    @Test
    @DisplayName("length and range bounds are 400s, not database errors")
    void rejectsOutOfBoundsValues() {
        PetForm longName = contractExample();
        longName.setName("R".repeat(101));
        assertEquals("name", assertThrows(ValidationException.class,
                () -> service.create(longName, OWNER_ID)).getField());

        PetForm oldPet = contractExample();
        oldPet.setAge(51);
        assertEquals("age", assertThrows(ValidationException.class,
                () -> service.create(oldPet, OWNER_ID)).getField());

        PetForm longDesc = contractExample();
        longDesc.setShortDesc("x".repeat(256));
        assertEquals("shortDesc", assertThrows(ValidationException.class,
                () -> service.create(longDesc, OWNER_ID)).getField());

        PetForm longBreed = contractExample();
        longBreed.setBreed("b".repeat(101));
        assertEquals("breed", assertThrows(ValidationException.class,
                () -> service.create(longBreed, OWNER_ID)).getField());
    }

    // ---------------------------------------------------------------- criterion 5

    @Test
    @DisplayName("criterion 5: only the owner may edit — an admin may not")
    void onlyTheOwnerMayEdit() {
        Pet pet = createPet(OWNER_ID);
        PetForm edit = contractExample();
        edit.setName("Rexy");

        ForbiddenException byStranger = assertThrows(ForbiddenException.class,
                () -> service.update(pet.getPetId(), edit, STRANGER_ID));
        assertEquals("NOT_OWNER", byStranger.getCode());

        // The contract grants admins delete rights only. Rewriting someone else's advert is a
        // different act from removing one that breaks a policy.
        assertThrows(ForbiddenException.class, () -> service.update(pet.getPetId(), edit, ADMIN_ID));

        assertEquals("Rex", pet.getPetName(), "a rejected edit must change nothing");
        assertEquals("Rexy", service.update(pet.getPetId(), edit, OWNER_ID).getName());
    }

    @Test
    @DisplayName("update cannot change the owner, the status or the creation time")
    void updateLeavesTheUneditableFieldsAlone() {
        Pet pet = createPet(OWNER_ID);
        pet.setStatus(Pet.PetStatus.ADOPTED);
        var createdAt = pet.getCreatedAt();

        PetForm edit = contractExample();
        edit.setName("Rexy");
        service.update(pet.getPetId(), edit, OWNER_ID);

        assertEquals(OWNER_ID, pet.getOwner().getUserId());
        assertEquals(Pet.PetStatus.ADOPTED, pet.getStatus());
        assertEquals(createdAt, pet.getCreatedAt());
    }

    @Test
    @DisplayName("editing a pet that does not exist is a 404, checked before the 403")
    void updateOfAnUnknownPetIsNotFound() {
        assertThrows(NotFoundException.class,
                () -> service.update(999L, contractExample(), STRANGER_ID));
    }

    // ---------------------------------------------------------------- criterion 6

    @Test
    @DisplayName("criterion 6: a concurrent edit is a 409 with code STALE_PET")
    void aConcurrentEditIsAConflict() {
        Pet pet = createPet(OWNER_ID);
        pets.failNextSaveWith = new OptimisticLockException("Row was updated by another transaction");

        ConflictException e = assertThrows(ConflictException.class,
                () -> service.update(pet.getPetId(), contractExample(), OWNER_ID));

        assertEquals("STALE_PET", e.getCode());
        assertNotNull(e.getCause(), "the provider's exception is kept for the log");
    }

    // ---------------------------------------------------------------- criteria 7 and 8

    @Test
    @DisplayName("criterion 7: the owner may delete")
    void ownerMayDelete() {
        Pet pet = createPet(OWNER_ID);

        service.delete(pet.getPetId(), OWNER_ID, false);

        assertTrue(pets.rows.isEmpty());
    }

    @Test
    @DisplayName("criterion 7: an admin who is not the owner may delete")
    void adminMayDeleteSomeoneElsesListing() {
        Pet pet = createPet(OWNER_ID);

        service.delete(pet.getPetId(), ADMIN_ID, true);

        assertTrue(pets.rows.isEmpty());
    }

    @Test
    @DisplayName("criterion 7: an unrelated user may not delete")
    void strangerMayNotDelete() {
        Pet pet = createPet(OWNER_ID);

        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> service.delete(pet.getPetId(), STRANGER_ID, false));

        assertEquals("NOT_OWNER_OR_ADMIN", e.getCode());
        assertEquals(1, pets.rows.size(), "a rejected delete must leave the listing alone");
    }

    @Test
    @DisplayName("criterion 8: a pet's images go with it")
    void deletingAPetTakesItsImages() {
        Pet pet = createPet(OWNER_ID);
        PetImage image = new PetImage();
        image.setImageUrl("/images/rex-main.jpg");
        image.setIsMain(true);
        image.setPet(pet);
        pet.getImages().add(image);

        service.delete(pet.getPetId(), OWNER_ID, false);

        assertTrue(pet.getImages().isEmpty());
        // The cascade is what actually removes the rows; assert it is still declared.
        assertTrue(cascadeIsDeclaredOnImages(),
                "Pet.images must keep cascade = ALL with orphanRemoval, or the rows survive the pet");
    }

    private static boolean cascadeIsDeclaredOnImages() {
        try {
            Field images = Pet.class.getDeclaredField("images");
            jakarta.persistence.OneToMany oneToMany = images.getAnnotation(jakarta.persistence.OneToMany.class);
            return oneToMany != null
                    && oneToMany.orphanRemoval()
                    && Arrays.asList(oneToMany.cascade()).contains(jakarta.persistence.CascadeType.ALL);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- criterion 9

    @Test
    @DisplayName("criterion 9: the owner's dashboard shows REMOVED listings too")
    void findByOwnerIncludesRemovedListings() {
        Pet available = createPet(OWNER_ID);
        Pet removed = createPet(OWNER_ID);
        removed.setStatus(Pet.PetStatus.REMOVED);
        createPet(STRANGER_ID);

        List<PetDTO> mine = service.findByOwner(OWNER_ID);

        assertEquals(List.of(removed.getPetId(), available.getPetId()),
                mine.stream().map(PetDTO::getId).toList());
        assertTrue(mine.stream().anyMatch(p -> "REMOVED".equals(p.getStatus())));
    }

    @Test
    @DisplayName("findByOwner is empty, not an error, for an unknown or null owner")
    void findByOwnerToleratesAnUnknownOwner() {
        assertTrue(service.findByOwner(999L).isEmpty());
        assertTrue(service.findByOwner(null).isEmpty());
    }

    // ---------------------------------------------------------------- requirement 7

    @Test
    @DisplayName("isOwner answers the question the JSF tier asks, without re-deriving the rule")
    void isOwnerAnswersWithoutThrowing() {
        Pet pet = createPet(OWNER_ID);

        assertTrue(service.isOwner(pet.getPetId(), OWNER_ID));
        assertFalse(service.isOwner(pet.getPetId(), STRANGER_ID));
        assertFalse(service.isOwner(999L, OWNER_ID), "an unknown pet is 'no', not an exception");
        assertFalse(service.isOwner(pet.getPetId(), null));
        assertFalse(service.isOwner(null, OWNER_ID));
    }

    // ---------------------------------------------------------------- T-34's moderation methods

    @Test
    @DisplayName("T-34 criterion 1: the admin listing shows every status, the gallery does not")
    void adminListingShowsEveryStatus() throws IOException {
        Pet hidden = persistedPetWithImages();
        service.changeStatus(hidden.getPetId(), "REMOVED");

        assertTrue(service.findGallery(PetFilter.none()).isEmpty(), "a REMOVED pet leaves the gallery");

        List<AdminPetDTO> all = service.findAllForAdmin(PetFilter.none());
        assertEquals(1, all.size());
        assertEquals("REMOVED", all.get(0).getStatus());
        assertEquals("Roy Stein", all.get(0).getOwnerName(), "the moderator sees who posted it");
        assertNotNull(all.get(0).getCreatedAt());
    }

    @Test
    @DisplayName("T-34 criterion 3: restoring a listing puts it back in the gallery")
    void restoringMakesAListingPublicAgain() throws IOException {
        Pet pet = persistedPetWithImages();
        service.changeStatus(pet.getPetId(), "REMOVED");

        PetDTO restored = service.changeStatus(pet.getPetId(), "available");

        assertEquals("AVAILABLE", restored.getStatus(), "case is forgiven");
        assertEquals(1, service.findGallery(PetFilter.none()).size());
    }

    @Test
    @DisplayName("only the two moderation statuses are accepted, and an unknown pet is 404")
    void refusesAnyOtherStatus() throws IOException {
        Pet pet = persistedPetWithImages();

        assertThrows(ValidationException.class, () -> service.changeStatus(pet.getPetId(), "ADOPTED"));
        assertThrows(ValidationException.class, () -> service.changeStatus(pet.getPetId(), null));
        assertThrows(NotFoundException.class, () -> service.changeStatus(999L, "REMOVED"));
    }
}
