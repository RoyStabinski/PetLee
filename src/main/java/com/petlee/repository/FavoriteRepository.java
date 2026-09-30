package com.petlee.repository;

import com.petlee.model.Favorite;
import com.petlee.model.FavoriteId;
import com.petlee.model.Pet;
import com.petlee.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Subgraph;
import java.util.List;

/** The members' saved pets. */
@ApplicationScoped
public class FavoriteRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    public boolean exists(Long userId, Long petId) {
        return em.find(Favorite.class, new FavoriteId(userId, petId)) != null;
    }

    /**
     * The member's saved pets, newest saved first, with each pet's category and images loaded by
     * the same query, since the caller maps them after the transaction has ended. Pets an
     * administrator has withdrawn (REMOVED) are left out; adopted ones stay, so the member sees
     * what became of them.
     *
     * <p>The pet, its category and its images are fetched through an entity graph, not
     * {@code JOIN FETCH f.pet p JOIN FETCH p.images}: that needs an alias on a fetch join, which
     * JPQL does not allow, and WildFly runs Hibernate in strict JPA compliance, so it is refused
     * outright. The graph is standard JPA and still becomes one SQL query with the joins.
     *
     * <p>DISTINCT folds the image rows back into one favourite each. ORDER BY names a field of the
     * selected entity, which both JPQL and PostgreSQL require alongside DISTINCT.
     */
    public List<Favorite> findVisibleByUser(Long userId) {
        EntityGraph<Favorite> graph = em.createEntityGraph(Favorite.class);
        Subgraph<Pet> pet = graph.addSubgraph("pet");
        pet.addAttributeNodes("category", "images");
        return em.createQuery(
                        "SELECT DISTINCT f FROM Favorite f"
                        + " WHERE f.id.userId = :userId AND f.pet.status <> :removed"
                        + " ORDER BY f.createdAt DESC", Favorite.class)
                .setParameter("userId", userId)
                .setParameter("removed", Pet.PetStatus.REMOVED)
                .setHint("jakarta.persistence.fetchgraph", graph)
                .getResultList();
    }

    /** @return the ids of the member's saved pets, with the same REMOVED rule as the list */
    public List<Long> findVisiblePetIds(Long userId) {
        return em.createQuery(
                        "SELECT f.id.petId FROM Favorite f"
                        + " WHERE f.id.userId = :userId AND f.pet.status <> :removed", Long.class)
                .setParameter("userId", userId)
                .setParameter("removed", Pet.PetStatus.REMOVED)
                .getResultList();
    }

    /**
     * Inserts now rather than at commit, so a duplicate key surfaces here, inside the
     * transaction that caused it, where the service can catch it.
     */
    public void persistNow(Long userId, Pet pet) {
        // A reference, not a load: the id came from a verified token, and the foreign key
        // refuses an id that does not exist.
        User user = em.getReference(User.class, userId);
        em.persist(new Favorite(user, pet));
        em.flush();
    }

    /** @return how many rows were deleted: 0 or 1 */
    public int delete(Long userId, Long petId) {
        return em.createQuery("DELETE FROM Favorite f"
                        + " WHERE f.id.userId = :userId AND f.id.petId = :petId")
                .setParameter("userId", userId)
                .setParameter("petId", petId)
                .executeUpdate();
    }
}
