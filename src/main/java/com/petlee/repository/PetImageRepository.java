package com.petlee.repository;

import com.petlee.model.PetImage;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Persistence operations for {@link PetImage}: the photographs a listing carries, and the
 * "exactly one main image" rule at the data layer (specification §11).
 *
 * <h2>One main image per pet</h2>
 * {@code ux_pet_image_main} is a partial unique index on {@code pet_id WHERE is_main = TRUE}, so
 * the database refuses a second main row outright. {@link #clearMainFlag(Long)} exists to make the
 * swap legal — read its warning before calling it.
 *
 * <h2>Not this class's job</h2>
 * No file I/O, no validation, no URL construction — T-16's {@code ImageStorageService} owns all
 * three. This class stores the path string it is handed.
 *
 * <p>It also does <strong>not</strong> clean up after a deleted pet. That is already guaranteed
 * twice: {@code cascade = ALL, orphanRemoval = true} on {@code Pet.images}, and
 * {@code ON DELETE CASCADE} on {@code pet_image.pet_id}. A third mechanism here would be one more
 * thing to keep in step with the other two.
 *
 * <h2>Every query is a literal with named parameters</h2>
 * Nothing is concatenated into JPQL.
 */
// @ApplicationScoped is repeated rather than inherited: the scope annotation is @Inherited, but in
// an implicit bean archive only a class carrying a bean-defining annotation of its own is
// discovered. See AbstractRepository's "Writing a subclass".
@ApplicationScoped
public class PetImageRepository extends AbstractRepository<PetImage, Integer> {

    public PetImageRepository() {
        super(PetImage.class);
    }

    /**
     * Every image belonging to a pet: the main one first, then the rest oldest-first by
     * {@code updatedAt}.
     *
     * <p>The order is fixed in the query rather than left to the provider, so T-30's detail
     * gallery does not reshuffle its thumbnails between reloads.
     *
     * @param petId the pet's id, may be {@code null}
     * @return the images in display order; an empty list for a {@code null} or unknown id, never
     *         {@code null}
     */
    public List<PetImage> findByPetId(Long petId) {
        if (petId == null) {
            return List.of();
        }
        return getEntityManager()
                .createQuery("SELECT i FROM PetImage i WHERE i.pet.petId = :petId "
                        + "ORDER BY i.isMain DESC, i.updatedAt ASC", PetImage.class)
                .setParameter("petId", petId)
                .getResultList();
    }

    /**
     * The pet's main image, the one T-11's gallery mapper puts in {@code mainImageUrl}.
     *
     * <p>A pet with images but no main one is an ordinary outcome, not an error: the result is
     * empty. At most one row can match, since {@code ux_pet_image_main} is unique; it is still read
     * as the first of a stream rather than through {@code getSingleResult()}, which turns "no row"
     * into an exception instead of an empty result.
     *
     * @param petId the pet's id, may be {@code null}
     * @return the main image, or empty when the pet has none; never {@code null}
     */
    public Optional<PetImage> findMainByPetId(Long petId) {
        if (petId == null) {
            return Optional.empty();
        }
        return getEntityManager()
                .createQuery("SELECT i FROM PetImage i WHERE i.pet.petId = :petId AND i.isMain = TRUE",
                        PetImage.class)
                .setParameter("petId", petId)
                .getResultStream()
                .findFirst();
    }

    /**
     * Clears the main flag on every image of a pet, in one bulk {@code UPDATE}.
     *
     * <p><strong>Call this before marking a new main image, never after.</strong> The order is the
     * whole point of the method: {@code ux_pet_image_main} rejects a second {@code is_main = TRUE}
     * row, so setting the new main first fails on the unique index. Clear, then set.
     *
     * <p>Both statements must run in <em>one</em> transaction, or a reader can see a pet with no
     * main image at all. T-16 owns that transaction.
     *
     * <p>A bulk update bypasses the persistence context: any {@link PetImage} already loaded in the
     * current one keeps its old {@code isMain} value in memory. Re-read after calling this rather
     * than trusting an instance you were already holding.
     *
     * @param petId the pet's id; a {@code null} id is a no-op rather than an error
     */
    public void clearMainFlag(Long petId) {
        if (petId == null) {
            return;
        }
        getEntityManager()
                .createQuery("UPDATE PetImage i SET i.isMain = FALSE WHERE i.pet.petId = :petId")
                .setParameter("petId", petId)
                .executeUpdate();
    }

    /**
     * Counts a pet's images, so T-16 can enforce its upload cap.
     *
     * <p>Counts rather than loading the rows: the cap check has no use for the images themselves.
     *
     * @param petId the pet's id, may be {@code null}
     * @return how many images the pet has; {@code 0} for a {@code null} or unknown id
     */
    public long countByPetId(Long petId) {
        if (petId == null) {
            return 0L;
        }
        return getEntityManager()
                .createQuery("SELECT COUNT(i) FROM PetImage i WHERE i.pet.petId = :petId", Long.class)
                .setParameter("petId", petId)
                .getSingleResult();
    }
}
