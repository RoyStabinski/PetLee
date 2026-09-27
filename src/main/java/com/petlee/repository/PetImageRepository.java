package com.petlee.repository;

import com.petlee.model.PetImage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

/**
 * A pet's images, read and written as rows of their own rather than through
 * {@code Pet.getImages()}, so an image change never bumps the pet's version.
 */
@ApplicationScoped
public class PetImageRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    /** @return the pet's images, oldest first */
    public List<PetImage> findByPet(Long petId) {
        return em.createQuery("SELECT i FROM PetImage i WHERE i.pet.petId = :petId"
                        + " ORDER BY i.uploadedAt ASC, i.imageId ASC", PetImage.class)
                .setParameter("petId", petId)
                .getResultList();
    }

    /** @return the image, only if it belongs to that pet */
    public Optional<PetImage> findInPet(Long petId, Long imageId) {
        if (petId == null || imageId == null) {
            return Optional.empty();
        }
        return em.createQuery("SELECT i FROM PetImage i"
                        + " WHERE i.imageId = :imageId AND i.pet.petId = :petId", PetImage.class)
                .setParameter("imageId", imageId)
                .setParameter("petId", petId)
                .getResultStream().findFirst();
    }

    public void persist(PetImage image) {
        em.persist(image);
    }

    public void remove(PetImage image) {
        em.remove(image);
    }

    /**
     * Writes pending changes now. The provider orders its own writes (inserts, then updates,
     * then deletes), and some sequences must reach the database in a different order to satisfy
     * {@code ux_pet_image_main}; the service calls this between those steps.
     */
    public void flush() {
        em.flush();
    }
}
