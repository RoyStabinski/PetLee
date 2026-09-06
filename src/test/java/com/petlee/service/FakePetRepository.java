package com.petlee.service;

import com.petlee.model.Pet;
import com.petlee.repository.PetFilter;
import com.petlee.repository.PetRepository;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * An in-memory {@link PetRepository} for the service unit tests.
 *
 * <p>The three listing methods reproduce the status and ordering rules T-08's Javadoc states, so
 * that a service test can tell {@code findByFilter} (the public gallery) apart from
 * {@code findAllForAdmin} (everything) — those rules live in the query, and the only thing
 * {@link PetService} can be held responsible for is calling the right one. The proof that the real
 * SQL behaves this way is T-38's integration test against a live database.
 */
class FakePetRepository extends PetRepository {

    final List<Pet> rows = new ArrayList<>();

    /** When set, the next {@link #save(Pet)} throws it — the concurrent-edit case. */
    RuntimeException failNextSaveWith;

    /** Counts calls, so a test can assert which listing query the service chose. */
    int findByFilterCalls;
    int findAllForAdminCalls;

    private long nextId = 10L;

    /**
     * Stands in for the provider's clock. Each persist gets a later timestamp than the last, so
     * "newest first" is decidable in a test rather than depending on two {@code now()} calls
     * landing in different nanoseconds.
     */
    private LocalDateTime clock = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Override
    public List<Pet> findByFilter(PetFilter filter) {
        findByFilterCalls++;
        return matching(filter).stream()
                .filter(p -> p.getStatus() == Pet.PetStatus.AVAILABLE)
                .toList();
    }

    @Override
    public List<Pet> findAllForAdmin(PetFilter filter) {
        findAllForAdminCalls++;
        return matching(filter);
    }

    @Override
    public List<Pet> findByOwnerId(Long ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        return newestFirst(rows.stream()
                .filter(p -> p.getOwner() != null && ownerId.equals(p.getOwner().getUserId()))
                .toList());
    }

    @Override
    public Optional<Pet> findDetailById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return rows.stream().filter(p -> id.equals(p.getPetId())).findFirst();
    }

    @Override
    public Optional<Pet> findById(Long id) {
        return findDetailById(id);
    }

    @Override
    public Pet save(Pet entity) {
        if (failNextSaveWith != null) {
            RuntimeException failure = failNextSaveWith;
            failNextSaveWith = null;
            throw failure;
        }
        if (entity.getPetId() == null) {
            entity.setPetId(nextId++);
            prePersist(entity);
            rows.add(entity);
        }
        return entity;
    }

    /**
     * What {@code Pet.onCreated()} does when the provider fires {@code @PrePersist}: stamp
     * {@code createdAt} and default the status. Neither field has a setter — the provider owns
     * them — so the fake reaches the field the same way the provider does.
     */
    private void prePersist(Pet entity) {
        clock = clock.plusMinutes(1);
        try {
            Field createdAt = Pet.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(entity, clock);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Pet.createdAt is no longer a field of that name", e);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(Pet.PetStatus.AVAILABLE);
        }
    }

    @Override
    public void delete(Pet entity) {
        // The row goes, and its images with it — cascade = ALL with orphanRemoval on Pet.images,
        // plus ON DELETE CASCADE in the schema. The list is cleared so a test can observe it.
        entity.getImages().clear();
        rows.remove(entity);
    }

    /** The most recently saved pet. */
    Pet last() {
        return rows.get(rows.size() - 1);
    }

    private List<Pet> matching(PetFilter filter) {
        PetFilter criteria = filter != null ? filter : PetFilter.none();
        return newestFirst(rows.stream()
                .filter(p -> criteria.getCategoryId() == null
                        || (p.getCategory() != null
                            && criteria.getCategoryId().equals(p.getCategory().getCategoryId())))
                .filter(p -> criteria.getSize() == null || criteria.getSize() == p.getSize())
                .filter(p -> criteria.getGender() == null || criteria.getGender() == p.getGender())
                .toList());
    }

    /** created_at DESC, as specification §11 requires and T-08 implements. */
    private static List<Pet> newestFirst(List<Pet> unordered) {
        return unordered.stream()
                .sorted(Comparator.comparing(Pet::getCreatedAt).reversed())
                .toList();
    }
}
