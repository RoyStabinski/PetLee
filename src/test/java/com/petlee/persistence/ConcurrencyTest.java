package com.petlee.persistence;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.User;
import com.petlee.repository.PetRepository;
import com.petlee.test.DatabaseTest;
import com.petlee.test.TestData;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Specification §4's concurrency control, proved rather than assumed — T-38 requirements 7 and 8.
 *
 * <p>{@code Pet.version} is a {@code @Version} column, and the whole point of it is that the second
 * of two concurrent edits is <strong>refused</strong> rather than silently overwriting the first.
 * That is a database and provider behaviour: no unit test with a fake repository can demonstrate it,
 * which is why it lives here. T-15 turns the failure into a 409 {@code STALE_PET}, and T-22 returns
 * it; this test is the layer underneath both.
 */
class ConcurrencyTest extends DatabaseTest {

    private User owner;
    private Category dogs;
    private Pet pet;

    @BeforeEach
    void seedAListing() {
        owner = TestData.aUser().build();
        dogs = TestData.aCategory().name("Dogs").build();
        persist(owner, dogs);

        pet = TestData.aPet().owner(owner).category(dogs).name("Rex").shortDesc("Original").build();
        persist(pet);
    }

    @Test
    @DisplayName("§4: the second of two concurrent edits is refused, not silently applied")
    void secondWriterLoses() {
        EntityManager first = freshEntityManager();
        EntityManager second = freshEntityManager();
        try {
            // Both read the same row, at the same version — two requests in flight at once.
            Pet asFirstSawIt = first.find(Pet.class, pet.getPetId());
            Pet asSecondSawIt = second.find(Pet.class, pet.getPetId());
            assertEquals(asFirstSawIt.getVersion(), asSecondSawIt.getVersion());

            first.getTransaction().begin();
            asFirstSawIt.setShortDesc("Edited by the first writer");
            first.getTransaction().commit();

            second.getTransaction().begin();
            asSecondSawIt.setShortDesc("Edited by the second writer");

            RuntimeException refused = assertCommitFails(second);
            assertTrue(refused instanceof OptimisticLockException || causeIsOptimisticLock(refused),
                    "the second commit must fail on the version column, but failed with: " + refused);
        } finally {
            close(first);
            close(second);
        }

        em.clear();
        Pet stored = em.find(Pet.class, pet.getPetId());
        assertEquals("Edited by the first writer", stored.getShortDesc(),
                "the point of the rule is that the first writer's data survives");
    }

    @Test
    @DisplayName("an uncontended edit still increments the version, so the next one can be detected")
    void versionAdvancesOnEveryUpdate() {
        Long before = em.find(Pet.class, pet.getPetId()).getVersion();

        inTransaction(manager -> manager.find(Pet.class, pet.getPetId()).setShortDesc("Edited"));
        em.clear();

        Long after = em.find(Pet.class, pet.getPetId()).getVersion();
        assertNotNull(after);
        assertTrue(after > before, "version went " + before + " -> " + after);
    }

    /**
     * T-38 requirement 8, in the shape the out-of-container tests can honestly take.
     *
     * <p>In production the connection pool is the server's, behind {@code jdbc/petlee}, and the
     * leak this guards against is a service that opens its own {@code EntityManager} instead of
     * using the injected persistence context. Here the pool is the provider's, but the failure mode
     * is identical: two hundred transactions, a third of them rolled back, and if anything holds a
     * connection afterwards the two hundred and first cannot start.
     */
    @Test
    @DisplayName("200 transactions, a third of them failing, leave the connections available")
    void transactionsDoNotLeakConnections() {
        PetRepository pets = inject(new PetRepository());

        for (int i = 0; i < 200; i++) {
            boolean shouldFail = i % 3 == 0;
            try {
                inTransaction(manager -> {
                    Pet stored = manager.find(Pet.class, pet.getPetId());
                    stored.setShortDesc("Edit " + stored.getVersion());
                    if (shouldFail) {
                        throw new IllegalStateException("deliberate rollback");
                    }
                });
            } catch (IllegalStateException deliberate) {
                em.clear();
            }
        }

        // The 201st caller: if a connection had been leaked, this is where it would hang or throw.
        assertEquals(1, pets.findByFilter(com.petlee.repository.PetFilter.none()).size());

        EntityManager afterwards = freshEntityManager();
        try {
            assertNotNull(afterwards.find(Pet.class, pet.getPetId()));
        } finally {
            close(afterwards);
        }
    }

    private static RuntimeException assertCommitFails(EntityManager manager) {
        try {
            if (manager.getTransaction().isActive()) {
                manager.getTransaction().commit();
            }
            throw new AssertionError("the conflicting commit was accepted");
        } catch (RollbackException | OptimisticLockException expected) {
            return expected;
        }
    }

    private static boolean causeIsOptimisticLock(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException) {
                return true;
            }
        }
        return false;
    }

    private static void close(EntityManager manager) {
        if (manager.getTransaction().isActive()) {
            manager.getTransaction().rollback();
        }
        manager.close();
    }
}
