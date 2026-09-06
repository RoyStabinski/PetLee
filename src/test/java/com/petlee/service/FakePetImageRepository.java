package com.petlee.service;

import com.petlee.model.PetImage;
import com.petlee.repository.PetImageRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * An in-memory {@link PetImageRepository} for the T-16 unit tests.
 *
 * <p>It reproduces the two behaviours the service depends on and that live in the query rather
 * than in Java: {@code findByPetId} orders main-first then oldest-first, and
 * {@code clearMainFlag} is a bulk update over one pet's rows. The database's part — the partial
 * unique index {@code ux_pet_image_main} — is not simulated; what the tests here can prove is that
 * the service never asks the database to hold two main rows at once.
 */
class FakePetImageRepository extends PetImageRepository {

    final List<PetImage> rows = new ArrayList<>();

    /** When set, the next {@link #save(PetImage)} throws it — the failed-insert case. */
    RuntimeException failNextSaveWith;

    private int nextId = 100;
    private LocalDateTime clock = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Override
    public List<PetImage> findByPetId(Long petId) {
        if (petId == null) {
            return List.of();
        }
        return rows.stream()
                .filter(i -> i.getPet() != null && petId.equals(i.getPet().getPetId()))
                .sorted(Comparator.comparing((PetImage i) -> Boolean.TRUE.equals(i.getIsMain())).reversed()
                        .thenComparing(PetImage::getUpdatedAt))
                .toList();
    }

    @Override
    public Optional<PetImage> findMainByPetId(Long petId) {
        return findByPetId(petId).stream().filter(i -> Boolean.TRUE.equals(i.getIsMain())).findFirst();
    }

    @Override
    public void clearMainFlag(Long petId) {
        findByPetId(petId).forEach(i -> i.setIsMain(false));
    }

    @Override
    public long countByPetId(Long petId) {
        return findByPetId(petId).size();
    }

    @Override
    public Optional<PetImage> findById(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        return rows.stream().filter(i -> id.equals(i.getImageId())).findFirst();
    }

    @Override
    public PetImage save(PetImage entity) {
        if (failNextSaveWith != null) {
            RuntimeException failure = failNextSaveWith;
            failNextSaveWith = null;
            throw failure;
        }
        stampUpdatedAt(entity);
        if (entity.getImageId() == null) {
            entity.setImageId(nextId++);
            rows.add(entity);
        }
        return entity;
    }

    @Override
    public void delete(PetImage entity) {
        rows.remove(entity);
    }

    /** What {@code PetImage.onUpsert()} does when the provider fires {@code @PrePersist}. */
    private void stampUpdatedAt(PetImage entity) {
        clock = clock.plusMinutes(1);
        entity.setUpdatedAt(clock);
    }
}
