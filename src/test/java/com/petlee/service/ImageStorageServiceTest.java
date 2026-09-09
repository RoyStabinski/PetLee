package com.petlee.service;

import com.petlee.test.Fakes;
import com.petlee.config.StorageConfig;
import com.petlee.dto.PetForm;
import com.petlee.dto.PetImageDTO;
import com.petlee.exception.ForbiddenException;
import com.petlee.exception.NotFoundException;
import com.petlee.exception.ValidationException;
import com.petlee.model.Pet;
import com.petlee.model.PetImage;
import com.petlee.model.User;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T-16's ten acceptance criteria. Criteria 3 and 5 are the security ones. */
class ImageStorageServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long STRANGER_ID = 2L;
    private static final Long ADMIN_ID = 3L;

    /** A minimal but genuine JPEG: the SOI marker, a JFIF header, the EOI marker. */
    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0xFF, 0xD9);
    private static final byte[] PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D);
    private static final byte[] GIF = bytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00);
    private static final byte[] WEBP = bytes(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x20);

    @TempDir
    Path uploadRoot;

    private Fakes.Images images;
    private Fakes.Pets pets;
    private Fakes.Users users;
    private ImageStorageService service;
    private Pet pet;

    @BeforeEach
    void setUp() {
        images = new Fakes.Images();
        pets = new Fakes.Pets();
        users = new Fakes.Users();
        PetService petService = new PetService(pets, users,
                new CategoryService(new Fakes.Categories()), new FixedRoot(uploadRoot));

        user(OWNER_ID, "roy");
        user(STRANGER_ID, "stranger");
        user(ADMIN_ID, "admin");

        service = new ImageStorageService(images, petService, new FixedRoot(uploadRoot));

        PetForm form = new PetForm();
        form.setName("Rex");
        form.setSize("MEDIUM");
        form.setGender("MALE");
        form.setCategoryId(1);
        pet = pets.findDetailById(petService.create(form, OWNER_ID).getId()).orElseThrow();
    }

    /** A {@link StorageConfig} pinned to the test's temporary directory. */
    static final class FixedRoot extends StorageConfig {
        private final Path root;

        FixedRoot(Path root) {
            this.root = root;
        }

        @Override
        public Path getUploadRoot() {
            return root;
        }
    }

    private void user(Long id, String username) {
        User u = new User();
        u.setUserId(id);
        u.setUserName(username);
        u.setFullName(username);
        u.setEmail(username + "@example.com");
        u.setRole(User.Role.USER);
        users.saved.add(u);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static InputStream stream(byte[] content) {
        return new ByteArrayInputStream(content);
    }

    private List<Path> filesInRoot() throws IOException {
        try (Stream<Path> files = Files.list(uploadRoot)) {
            return files.toList();
        }
    }

    private PetImageDTO storeJpeg(boolean isMain, Long caller) {
        return service.store(pet.getPetId(), stream(JPEG), "photo.jpg", "image/jpeg",
                JPEG.length, isMain, caller);
    }

    // ---------------------------------------------------------------- criterion 1

    @Test
    @DisplayName("criterion 1: the owner's JPEG is stored and the file exists under the root")
    void storesAValidJpegForTheOwner() throws IOException {
        PetImageDTO dto = storeJpeg(false, OWNER_ID);

        assertEquals(1, filesInRoot().size());
        assertTrue(dto.getImageUrl().startsWith("/images/"));

        Path stored = uploadRoot.resolve(dto.getImageUrl().substring("/images/".length()));
        assertTrue(Files.exists(stored), "the file named in image_url must exist");
        assertEquals(JPEG.length, Files.size(stored));
    }

    @Test
    @DisplayName("all four accepted formats are recognised by their bytes")
    void acceptsTheFourFormats() {
        assertTrue(service.store(pet.getPetId(), stream(PNG), "a.png", "image/png",
                PNG.length, false, OWNER_ID).getImageUrl().endsWith(".png"));
        assertTrue(service.store(pet.getPetId(), stream(GIF), "a.gif", "image/gif",
                GIF.length, false, OWNER_ID).getImageUrl().endsWith(".gif"));
        assertTrue(service.store(pet.getPetId(), stream(WEBP), "a.webp", "image/webp",
                WEBP.length, false, OWNER_ID).getImageUrl().endsWith(".webp"));
        assertTrue(storeJpeg(false, OWNER_ID).getImageUrl().endsWith(".jpg"));
    }

    // ---------------------------------------------------------------- criterion 2

    @Test
    @DisplayName("criterion 2: a non-owner is refused and no file is written")
    void refusesANonOwnerWithoutWritingAnything() throws IOException {
        ForbiddenException e = assertThrows(ForbiddenException.class, () -> storeJpeg(true, STRANGER_ID));

        assertEquals("NOT_OWNER", e.getCode());
        assertTrue(filesInRoot().isEmpty(), "authorisation must happen before a single byte is written");
        assertTrue(images.rows.isEmpty());
    }

    @Test
    @DisplayName("an unknown pet is a 404, checked before the 403")
    void unknownPetIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.store(999L, stream(JPEG), "a.jpg",
                "image/jpeg", JPEG.length, false, OWNER_ID));
    }

    // ---------------------------------------------------------------- criterion 3 (security)

    @Test
    @DisplayName("criterion 3: a text file renamed .jpg and declared image/jpeg is rejected")
    void rejectsAScriptWearingAJpegHeader() throws IOException {
        byte[] notAnImage = "#!/bin/sh\nrm -rf /\n".getBytes(StandardCharsets.UTF_8);

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.store(pet.getPetId(), stream(notAnImage), "photo.jpg", "image/jpeg",
                        notAnImage.length, false, OWNER_ID));

        assertEquals("UNSUPPORTED_IMAGE_TYPE", e.getCode());
        assertTrue(filesInRoot().isEmpty(), "a rejected upload must leave nothing on disk");
    }

    @Test
    @DisplayName("criterion 3: the bytes decide the type, not the declared header")
    void theBytesDecideNotTheHeader() throws IOException {
        // A real PNG declared as a JPEG is still stored - as a PNG. The header is advisory.
        PetImageDTO dto = service.store(pet.getPetId(), stream(PNG), "photo.jpg", "image/jpeg",
                PNG.length, false, OWNER_ID);

        assertTrue(dto.getImageUrl().endsWith(".png"),
                "the extension follows the detected type, not the declared one");
        assertEquals(1, filesInRoot().size());
    }

    // ---------------------------------------------------------------- criterion 4

    @Test
    @DisplayName("criterion 4: a 6 MB file is rejected, whatever size it declares")
    void rejectsAnOversizedFile() throws IOException {
        byte[] tooBig = new byte[6 * 1024 * 1024];
        System.arraycopy(JPEG, 0, tooBig, 0, JPEG.length);

        // Declared honestly.
        assertEquals("FILE_TOO_LARGE", assertThrows(ValidationException.class,
                () -> service.store(pet.getPetId(), stream(tooBig), "big.jpg", "image/jpeg",
                        tooBig.length, false, OWNER_ID)).getCode());

        // And declared as a lie: the cap is enforced again while reading.
        assertEquals("FILE_TOO_LARGE", assertThrows(ValidationException.class,
                () -> service.store(pet.getPetId(), stream(tooBig), "big.jpg", "image/jpeg",
                        12L, false, OWNER_ID)).getCode());

        assertTrue(filesInRoot().isEmpty());
    }

    @Test
    @DisplayName("criterion 4: the ninth image for one pet is rejected")
    void rejectsANinthImage() throws IOException {
        for (int i = 0; i < 8; i++) {
            storeJpeg(false, OWNER_ID);
        }

        ValidationException e = assertThrows(ValidationException.class, () -> storeJpeg(false, OWNER_ID));

        assertEquals("TOO_MANY_IMAGES", e.getCode());
        assertEquals(8, filesInRoot().size(), "the rejected ninth must not have been written");
    }

    @Test
    @DisplayName("an empty upload is a 400, not a zero-byte file")
    void rejectsAnEmptyUpload() throws IOException {
        assertEquals("EMPTY_FILE", assertThrows(ValidationException.class,
                () -> service.store(pet.getPetId(), stream(new byte[0]), "a.jpg", "image/jpeg",
                        0L, false, OWNER_ID)).getCode());
        assertTrue(filesInRoot().isEmpty());
    }

    // ---------------------------------------------------------------- criterion 5 (security)

    @Test
    @DisplayName("criterion 5: a traversing filename produces a generated name inside the root")
    void defeatsPathTraversalByGeneratingTheName() throws IOException {
        PetImageDTO dto = service.store(pet.getPetId(), stream(JPEG),
                "../../../evil.jpg", "image/jpeg", JPEG.length, false, OWNER_ID);

        List<Path> written = filesInRoot();
        assertEquals(1, written.size());

        String fileName = written.get(0).getFileName().toString();
        assertTrue(fileName.matches("^" + pet.getPetId() + "_[0-9a-f-]{36}\\.jpg$"),
                "expected a generated <petId>_<UUID>.jpg name, got: " + fileName);
        assertFalse(fileName.contains("evil"), "no part of the client's filename may survive");
        assertEquals("/images/" + fileName, dto.getImageUrl());

        // And nothing landed outside the root.
        assertTrue(Files.isDirectory(uploadRoot));
        assertFalse(Files.exists(uploadRoot.getParent().resolve("evil.jpg")));
    }

    @Test
    @DisplayName("criterion 5: null bytes and unicode tricks in the filename are equally irrelevant")
    void ignoresEveryOtherFilenameTrick() throws IOException {
        // A NUL, and a right-to-left override - the two tricks that make a filename render as
        // something other than what it is. Neither reaches a Path, because none of this string does.
        String hostile = "a" + (char) 0x00 + ".jpg" + (char) 0x202E + " gnp.";

        service.store(pet.getPetId(), stream(JPEG), hostile, "image/jpeg", JPEG.length, false, OWNER_ID);

        String fileName = filesInRoot().get(0).getFileName().toString();
        assertTrue(fileName.matches("^" + pet.getPetId() + "_[0-9a-f-]{36}\\.jpg$"), fileName);
    }

    // ---------------------------------------------------------------- criteria 6 and 7

    @Test
    @DisplayName("criterion 7: the first image is main even when isMain=false is sent")
    void theFirstImageIsAlwaysMain() {
        PetImageDTO first = storeJpeg(false, OWNER_ID);

        assertTrue(first.getIsMain(),
                "a listing with photographs but no main one renders a broken gallery card");
    }

    @Test
    @DisplayName("criterion 6: a second isMain=true upload leaves exactly one main row")
    void onlyOneImageIsEverMain() {
        PetImageDTO first = storeJpeg(false, OWNER_ID);
        PetImageDTO second = storeJpeg(true, OWNER_ID);

        assertEquals(1, images.rows.stream().filter(i -> Boolean.TRUE.equals(i.getIsMain())).count());
        assertEquals(second.getId(), images.findMainByPetId(pet.getPetId()).orElseThrow().getImageId());
        assertFalse(images.findById(first.getId()).orElseThrow().getIsMain());
    }

    @Test
    @DisplayName("a later isMain=false upload does not disturb the existing main image")
    void anOrdinaryUploadLeavesTheMainImageAlone() {
        PetImageDTO first = storeJpeg(false, OWNER_ID);
        PetImageDTO second = storeJpeg(false, OWNER_ID);

        assertTrue(images.findById(first.getId()).orElseThrow().getIsMain());
        assertFalse(images.findById(second.getId()).orElseThrow().getIsMain());
    }

    // ---------------------------------------------------------------- criterion 8

    @Test
    @DisplayName("criterion 8: a failed insert leaves no orphan file")
    void aFailedInsertLeavesNoFile() throws IOException {
        images.failNextSaveWith = new PersistenceException("insert failed");

        assertThrows(PersistenceException.class, () -> storeJpeg(false, OWNER_ID));

        assertTrue(filesInRoot().isEmpty(),
                "the file must be removed when the row it belongs to never went in");
    }

    // ---------------------------------------------------------------- criterion 9

    @Test
    @DisplayName("criterion 9: deleting the main image of a three-image pet promotes another")
    void deletingTheMainImagePromotesTheOldestSurvivor() throws IOException {
        PetImageDTO main = storeJpeg(false, OWNER_ID);   // the first, so main
        PetImageDTO second = storeJpeg(false, OWNER_ID);
        storeJpeg(false, OWNER_ID);

        service.deleteImage(main.getId(), OWNER_ID, false);

        assertEquals(2, images.rows.size());
        assertEquals(2, filesInRoot().size(), "the deleted image's file goes with its row");
        assertEquals(second.getId(), images.findMainByPetId(pet.getPetId()).orElseThrow().getImageId(),
                "the oldest survivor is promoted, so the pet still has a main image");
    }

    @Test
    @DisplayName("deleting the last image leaves no main image and no error")
    void deletingTheOnlyImageIsFine() throws IOException {
        PetImageDTO only = storeJpeg(true, OWNER_ID);

        service.deleteImage(only.getId(), OWNER_ID, false);

        assertTrue(images.rows.isEmpty());
        assertTrue(filesInRoot().isEmpty());
    }

    @Test
    @DisplayName("only the owner or an administrator may delete a photograph")
    void deleteIsOwnerOrAdmin() throws IOException {
        PetImageDTO image = storeJpeg(true, OWNER_ID);

        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> service.deleteImage(image.getId(), STRANGER_ID, false));
        assertEquals("NOT_OWNER_OR_ADMIN", e.getCode());
        assertEquals(1, filesInRoot().size(), "a refused delete must leave the file alone");

        service.deleteImage(image.getId(), ADMIN_ID, true);
        assertTrue(filesInRoot().isEmpty());
    }

    @Test
    @DisplayName("deleting an image that does not exist is a 404")
    void deletingAnUnknownImageIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.deleteImage(999, OWNER_ID, false));
    }

    // ---------------------------------------------------------------- criterion 10

    @Test
    @DisplayName("criterion 10: image_url is a public URL path, never a filesystem path")
    void storesAPublicUrlNotAFilesystemPath() {
        String url = storeJpeg(false, OWNER_ID).getImageUrl();

        assertTrue(url.startsWith("/images/"), url);
        assertFalse(url.substring("/images/".length()).contains("/"), url);
        assertFalse(url.contains("\\"), url);
        assertFalse(url.contains(uploadRoot.toString()),
                "the server's directory layout must not leak into the JSON contract");
    }

    @Test
    @DisplayName("both sides of the pet-image relationship are maintained")
    void keepsBothSidesOfTheRelationship() {
        PetImageDTO dto = storeJpeg(false, OWNER_ID);

        // Setting only the owning side leaves the Pet cached with an empty images collection, and
        // a later LEFT JOIN FETCH hands that cached object back reporting no images at all.
        assertEquals(1, pet.getImages().size());
        assertEquals(dto.getId(), pet.getImages().get(0).getImageId());

        PetImage stored = images.findById(dto.getId()).orElseThrow();
        assertEquals(pet.getPetId(), stored.getPet().getPetId());
    }
}
