package com.petlee.service;

import com.petlee.model.Favorite;
import com.petlee.model.Pet;
import com.petlee.repository.FavoriteRepository;
import com.petlee.repository.PetRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A member's saved pets. Adding and removing are both idempotent, so a double click, a retry
 * or two open tabs never produce an error. The member is always the caller id passed in.
 */
@ApplicationScoped
public class FavoriteService {

    private FavoriteRepository favorites;
    private PetRepository pets;

    /** Self-reference, so {@link #add} reaches {@link #insert}'s own transaction through the proxy. */
    @Inject
    private FavoriteService self;

    /** For CDI only. */
    protected FavoriteService() {
    }

    @Inject
    public FavoriteService(FavoriteRepository favorites, PetRepository pets) {
        this.favorites = favorites;
        this.pets = pets;
    }

    /**
     * Saves a pet for the member. Saving one that is already saved succeeds and changes nothing.
     *
     * <p>Runs outside any transaction on purpose. The insert runs in a transaction of its own
     * ({@link #insert}); if two requests race past the "already saved?" check, the primary key
     * refuses the second insert, and that failure has to roll back only the insert's own
     * transaction. Were it the caller's, JTA would already have marked it rollback-only, and
     * catching the exception here could not stop the commit from failing.
     *
     * @param userId the member, from the verified token
     * @param petId  the pet to save
     * @throws AppException 404 if there is no such pet, or it is not AVAILABLE
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void add(Long userId, Long petId) {
        try {
            self.insert(userId, petId);
        } catch (PersistenceException raced) {
            // The concurrent request won: its row is the one we were about to insert, so the
            // outcome the caller asked for holds and this counts as success. Checked rather than
            // assumed: any other constraint failure (the pet deleted in between, say) still fails.
            if (!favorites.exists(userId, petId)) {
                throw raced;
            }
        }
    }

    /**
     * The insert, in a transaction of its own; see {@link #add}. Public only so the call goes
     * through the CDI proxy, which is what applies the transaction.
     *
     * @throws AppException 404 if there is no such pet, or it is not AVAILABLE
     * @throws PersistenceException if the database refuses the row, such as a duplicate key
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void insert(Long userId, Long petId) {
        Pet pet = pets.findById(petId)
                .filter(p -> p.getStatus() == Pet.PetStatus.AVAILABLE)
                .orElseThrow(() -> new AppException(404, "Pet not found"));
        if (favorites.exists(userId, petId)) {
            return;
        }
        favorites.persistNow(userId, pet);
    }

    /**
     * Forgets a saved pet. Forgetting one that is not saved, or no longer exists, succeeds.
     *
     * @param userId the member, from the verified token
     * @param petId  the pet
     */
    @Transactional
    public void remove(Long userId, Long petId) {
        favorites.delete(userId, petId);
    }

    /**
     * @param userId the member
     * @return the saved pets, newest saved first, with their images already loaded. Withdrawn
     *         (REMOVED) pets are left out; adopted ones stay, so the member sees what happened.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Pet> list(Long userId) {
        return favorites.findVisibleByUser(userId).stream().map(Favorite::getPet).toList();
    }

    /**
     * @param userId the member
     * @return the ids of the saved pets, under the same REMOVED rule as {@link #list}, so the
     *         count a page shows matches the list it links to
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public Set<Long> ids(Long userId) {
        return new LinkedHashSet<>(favorites.findVisiblePetIds(userId));
    }
}
