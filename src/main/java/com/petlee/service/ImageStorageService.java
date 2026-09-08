package com.petlee.service;

import com.petlee.config.StorageConfig;
import com.petlee.dto.PetImageDTO;
import com.petlee.exception.ForbiddenException;
import com.petlee.exception.NotFoundException;
import com.petlee.exception.ValidationException;
import com.petlee.mapper.PetMapper;
import com.petlee.model.Pet;
import com.petlee.model.PetImage;
import com.petlee.repository.PetImageRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores an uploaded photograph and records it against a pet, keeping specification §11's "exactly
 * one main image" invariant.
 *
 * <h2>What this class refuses to trust</h2>
 * <ul>
 *   <li><strong>The declared content type.</strong> It is set by the client and means nothing. The
 *       type is decided from the file's leading bytes; a shell script sent as
 *       {@code Content-Type: image/jpeg} is the classic upload attack and is rejected here.</li>
 *   <li><strong>The original filename.</strong> It is never persisted and never reaches a
 *       {@link Path}. The stored name is generated — {@code <petId>_<UUID>.<ext>} — which defeats
 *       path traversal, embedded null bytes and unicode filename tricks in one move, because none
 *       of the attacker's string survives.</li>
 *   <li><strong>The declared size.</strong> It is an early reject only; the cap is enforced again
 *       while reading, so a lying {@code Content-Length} buys nothing.</li>
 * </ul>
 *
 * <h2>Order of operations</h2>
 * Authorisation happens before a single byte is written, and the bytes are validated in memory
 * before the file is created, so a rejected upload leaves nothing behind. Only then is the file
 * written and the row inserted; if the insert fails the file is deleted again, so the two cannot
 * drift apart.
 *
 * <p>The remaining window is a transaction that rolls back <em>after</em> this method returns.
 * {@code AbstractRepository.save} flushes, so a constraint violation is caught here, but a
 * rollback caused by later work in the same transaction would still orphan the file. T-23 keeps
 * the upload request to this one call so that window stays closed.
 *
 * <h2>This service does not parse multipart</h2>
 * It receives a plain {@link InputStream}. T-23 owns the {@code Part} handling, which keeps this
 * class free of servlet imports and testable without a container.
 */
@ApplicationScoped
public class ImageStorageService {

    private static final Logger LOGGER = Logger.getLogger(ImageStorageService.class.getName());

    /** Specification-free but necessary: 5 MB is generous for a photograph and cheap to store. */
    static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Eight photographs is more than any adoption advert needs, and bounds one pet's disk use. */
    static final int MAX_IMAGES_PER_PET = 8;

    /**
     * The stored filename, and the only shape {@link #deleteImage} will resolve against the upload
     * root. Anything else in {@code image_url} — a separator, a {@code ..}, an absolute path — is
     * refused rather than followed, even though every value in that column was generated here.
     */
    private PetImageRepository images;
    private PetService pets;
    private StorageConfig storage;

    /** For CDI only — an {@code @ApplicationScoped} proxy needs a no-argument constructor. */
    protected ImageStorageService() {
    }

    @Inject
    public ImageStorageService(PetImageRepository images, PetService pets, StorageConfig storage) {
        this.images = images;
        this.pets = pets;
        this.storage = storage;
    }

    /**
     * Stores one photograph against a pet — the body of {@code POST /api/pets/{id}/images}.
     *
     * @param petId            the pet the photograph belongs to
     * @param data             the file's bytes; read once and closed by the caller
     * @param originalFilename the client's filename. Recorded nowhere and used for nothing: it is
     *                         accepted only so the caller does not have to pretend it has none.
     * @param contentType      the client's declared type. <strong>Ignored for the decision</strong>
     *                         — see {@link #detectType} — and kept only for the log line that says
     *                         what was claimed versus what it turned out to be.
     * @param sizeBytes        the client's declared size, or a negative number when unknown; an
     *                         early reject only
     * @param isMain           whether the caller asked for this to be the main image. Forced to
     *                         {@code true} for a pet's first image regardless — a listing with
     *                         photographs but no main one renders a broken gallery card.
     * @param callerUserId     the session user's id
     * @return the stored image
     * @throws ForbiddenException <strong>403</strong> — {@code api-contract.md} marks this endpoint
     *         "auth + owner", so only the pet's owner may add to it. Checked first, before any byte
     *         is read or written.
     * @throws NotFoundException <strong>404</strong> — no pet has that id
     * @throws ValidationException <strong>400</strong> — the file is empty, larger than 5 MB, not
     *         one of the four accepted image types, or the pet already has eight images
     */
    @Transactional
    public PetImageDTO store(Long petId, InputStream data, String originalFilename,
                             String contentType, long sizeBytes, boolean isMain, Long callerUserId) {

        // 1. Existence, then authorisation - both before anything is read and long before
        //    anything is written. This order is why an owner uploading to a listing that has just
        //    been deleted gets a 404 rather than a puzzling 403.
        Pet pet = pets.requireById(petId);
        if (!pets.isOwner(petId, callerUserId)) {
            throw new ForbiddenException("NOT_OWNER", "Only the owner of a listing can add photographs to it");
        }

        // 2. Cheap rejects: the declared size, and the per-pet cap.
        if (sizeBytes > MAX_BYTES) {
            throw tooLarge();
        }
        long existing = images.countByPetId(petId);
        if (existing >= MAX_IMAGES_PER_PET) {
            throw new ValidationException("file", "TOO_MANY_IMAGES",
                    "A listing may have at most " + MAX_IMAGES_PER_PET + " photographs");
        }

        // 3. The bytes decide what this is, not the header. Read into memory first, so a rejected
        //    upload never creates a file.
        byte[] bytes = readAtMost(data);
        ImageType type = detectType(bytes).orElseThrow(() -> new ValidationException("file",
                "UNSUPPORTED_IMAGE_TYPE",
                "Only JPEG, PNG, WebP and GIF images are accepted"));
        if (!type.matchesDeclared(contentType)) {
            LOGGER.log(Level.INFO, () -> "Upload for pet " + petId + " declared " + contentType
                    + " but is " + type.mediaType + "; the bytes decide");
        }

        // 4. The name is generated. None of the client's string reaches the filesystem.
        String fileName = pet.getPetId() + "_" + UUID.randomUUID() + "." + type.extension;
        Path target = resolveInsideRoot(fileName);
        write(bytes, target);

        try {
            // 5. The first image is main whatever was asked for; otherwise honour the request.
            boolean main = existing == 0 || isMain;
            if (main) {
                // Clear first, then set. ux_pet_image_main rejects a second is_main = TRUE row,
                // so the other order fails on the unique index (T-09).
                images.clearMainFlag(petId);
            }

            PetImage image = new PetImage();
            image.setImageUrl(StorageConfig.PUBLIC_URL_PREFIX + fileName);
            image.setIsMain(main);
            image.setPet(pet);
            // Both sides, deliberately: setting only the owning side leaves the Pet in the
            // provider's cache holding an already-instantiated empty images collection, and a
            // later LEFT JOIN FETCH hands that cached object back reporting no images at all.
            pet.getImages().add(image);

            PetImage saved = images.save(image);
            LOGGER.log(Level.INFO, () -> "Stored " + saved.getImageUrl() + " for pet " + petId);
            return PetMapper.toImageDto(saved);
        } catch (RuntimeException e) {
            // The row did not go in, so the file must not stay. Otherwise every failed upload
            // leaves a byte-for-byte copy of a user's photograph that nothing references.
            storage.deleteStored(StorageConfig.PUBLIC_URL_PREFIX + target.getFileName());
            throw e;
        }
    }

    /**
     * Removes one photograph, both the row and the file.
     *
     * <p>If it was the main image, the oldest remaining photograph is promoted, so specification
     * §11's invariant — a pet with photographs has a main one — survives the deletion.
     *
     * @param imageId       the image's id
     * @param callerUserId  the session user's id
     * @param callerIsAdmin whether that user's role is {@code ADMIN}, decided by the REST tier from
     *                      the session. The parameter is not in T-16's original signature; it is
     *                      here because the task requires "owner-or-admin" and the service must
     *                      never work the caller's role out for itself (T-15, requirement 8).
     * @throws NotFoundException <strong>404</strong> — no image has that id
     * @throws ForbiddenException <strong>403</strong> — not the owner and not an administrator
     */
    @Transactional
    public void deleteImage(Integer imageId, Long callerUserId, boolean callerIsAdmin) {
        PetImage image = images.findById(imageId)
                .orElseThrow(() -> new NotFoundException("IMAGE_NOT_FOUND", "No such image: " + imageId));

        Pet pet = image.getPet();
        Long petId = pet == null ? null : pet.getPetId();

        if (!callerIsAdmin && !pets.isOwner(petId, callerUserId)) {
            throw new ForbiddenException("NOT_OWNER_OR_ADMIN",
                    "Only the owner of a listing, or an administrator, can remove its photographs");
        }

        boolean wasMain = Boolean.TRUE.equals(image.getIsMain());
        String url = image.getImageUrl();

        if (pet != null) {
            // Both sides again, for the same reason as in store.
            pet.getImages().remove(image);
        }
        images.delete(image);

        if (wasMain) {
            promoteOldestToMain(petId);
        }

        // The file goes last: a failed delete must not be the reason a row survives, and an
        // unreferenced file is a smaller problem than a row pointing at nothing.
        storage.deleteStored(url);
        LOGGER.log(Level.INFO, () -> "Deleted image " + imageId + " (" + url + ")");
    }

    /**
     * Makes the oldest remaining photograph the main one. A pet with no photographs left needs no
     * main image, so the empty case is a no-op rather than an error.
     */
    private void promoteOldestToMain(Long petId) {
        // findByPetId orders main-first then oldest-first; with the main row gone, the first
        // element is the oldest survivor.
        List<PetImage> remaining = images.findByPetId(petId);
        if (remaining.isEmpty()) {
            return;
        }
        PetImage oldest = remaining.get(0);
        oldest.setIsMain(true);
        images.save(oldest);
        LOGGER.log(Level.FINE, () -> "Promoted image " + oldest.getImageId() + " to main for pet " + petId);
    }

    /**
     * Reads at most {@link #MAX_BYTES} plus one byte. The extra byte is the whole trick: if it
     * arrives, the file is over the cap, and that is known without ever holding more than 5 MB and
     * one byte in memory or trusting what the client declared.
     */
    private static byte[] readAtMost(InputStream data) {
        if (data == null) {
            throw new ValidationException("file", "EMPTY_FILE", "No file was uploaded");
        }
        byte[] bytes;
        try {
            bytes = data.readNBytes((int) MAX_BYTES + 1);
        } catch (IOException e) {
            throw new ValidationException("file", "UNREADABLE_FILE", "The upload could not be read");
        }
        if (bytes.length == 0) {
            throw new ValidationException("file", "EMPTY_FILE", "No file was uploaded");
        }
        if (bytes.length > MAX_BYTES) {
            throw tooLarge();
        }
        return bytes;
    }

    private static ValidationException tooLarge() {
        return new ValidationException("file", "FILE_TOO_LARGE",
                "A photograph may be at most " + (MAX_BYTES / (1024 * 1024)) + " MB");
    }

    /**
     * Identifies the file from its leading bytes.
     *
     * @return the type, or empty when the bytes are not one of the four accepted formats
     */
    private static Optional<ImageType> detectType(byte[] bytes) {
        for (ImageType type : ImageType.values()) {
            if (type.matches(bytes)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves a generated filename against the upload root and proves the result is still inside
     * it.
     *
     * <p>The name is generated, so this cannot currently fail — which is exactly why it is here.
     * It is the check that keeps a future change to the naming scheme from quietly becoming a path
     * traversal, and it costs one comparison per upload.
     */
    private Path resolveInsideRoot(String fileName) {
        Path root = storage.getUploadRoot();
        Path candidate = root.resolve(fileName).normalize();
        if (!candidate.startsWith(root)) {
            throw new ValidationException("file", "INVALID_FILENAME", "The file could not be stored");
        }
        return candidate;
    }

    private void write(byte[] bytes, Path target) {
        try {
            // CREATE_NEW, not a truncating write: a UUID collision is astronomically unlikely, and
            // if one ever happened the right outcome is a loud failure, not one user's photograph
            // silently replacing another's.
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            // Canonicalise after the write, when the path really exists, and check containment
            // once more against the resolved root. toRealPath follows symlinks, so this is the
            // check that catches a root containing a link out of itself — something normalize()
            // on the way in cannot see.
            Path real = target.toRealPath();
            if (!real.startsWith(storage.getUploadRoot())) {
                Files.deleteIfExists(real);
                throw new ValidationException("file", "INVALID_FILENAME", "The file could not be stored");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the uploaded photograph", e);
        }
    }

    /**
     * The four accepted formats and the byte signatures that identify them.
     *
     * <p>Signatures, not extensions and not headers: these are the first bytes of the file itself,
     * which is the only part of an upload the client cannot lie about while still sending a real
     * image.
     */
    enum ImageType {
        /** {@code FF D8 FF} — every JPEG variant starts with the SOI marker. */
        JPEG("image/jpeg", "jpg", new int[]{0xFF, 0xD8, 0xFF}),
        /** The 8-byte PNG signature, including the CRLF pair that detects text-mode transfers. */
        PNG("image/png", "png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
        /** {@code GIF87a} or {@code GIF89a}; the shared prefix is enough to tell it apart. */
        GIF("image/gif", "gif", new int[]{0x47, 0x49, 0x46, 0x38}),
        /** A RIFF container whose form type is {@code WEBP}, at offset 8. */
        WEBP("image/webp", "webp", new int[]{0x52, 0x49, 0x46, 0x46}) {
            @Override
            boolean matches(byte[] bytes) {
                return super.matches(bytes) && bytes.length >= 12
                        && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            }
        };

        private final String mediaType;
        private final String extension;
        private final int[] signature;

        ImageType(String mediaType, String extension, int[] signature) {
            this.mediaType = mediaType;
            this.extension = extension;
            this.signature = signature;
        }

        boolean matches(byte[] bytes) {
            if (bytes.length < signature.length) {
                return false;
            }
            for (int i = 0; i < signature.length; i++) {
                if ((bytes[i] & 0xFF) != signature[i]) {
                    return false;
                }
            }
            return true;
        }

        boolean matchesDeclared(String contentType) {
            return contentType != null
                    && mediaType.equals(contentType.trim().toLowerCase(Locale.ROOT));
        }
    }
}
