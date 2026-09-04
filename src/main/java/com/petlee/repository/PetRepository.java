package com.petlee.repository;

import com.petlee.model.Pet;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistence operations for {@link Pet}: the gallery, detail and dashboard queries the product is
 * built around (specification §9.2).
 *
 * <h2>Which statuses each method returns</h2>
 * <ul>
 *   <li>{@link #findByFilter(PetFilter)} — {@code AVAILABLE} only. The public gallery never shows
 *       an adopted or removed listing.</li>
 *   <li>{@link #findDetailById(Long)} — any status. The caller decides what a guest may see.</li>
 *   <li>{@link #findByOwnerId(Long)} — any status. An owner must see their own adopted and removed
 *       listings.</li>
 *   <li>{@link #findAllForAdmin(PetFilter)} — any status. T-34 only.</li>
 * </ul>
 *
 * <h2>Ordering</h2>
 * Every list is ordered {@code created_at DESC} — specification §11 requires newest first, and it
 * is not caller-configurable.
 *
 * <h2>No N+1</h2>
 * Each method is a single query that {@code LEFT JOIN FETCH}es what its screen renders, so a
 * 50-pet gallery costs one statement rather than 101. Because a fetch join over the {@code images}
 * collection repeats the pet row once per image, the queries are {@code DISTINCT}; the provider
 * folds those repeats back into one {@link Pet} without disturbing the order.
 *
 * <p>Fetching eagerly here is also what keeps the mappers working: the {@code EntityManager} is
 * closed by the time T-11 converts an entity to a DTO, so anything left lazy would throw
 * {@link jakarta.persistence.LazyInitializationException}.
 *
 * <h2>Criteria API, no concatenation</h2>
 * Filters are built as {@link Predicate}s from typed {@link PetFilter} values. No caller input is
 * ever spliced into a query string.
 */
// @ApplicationScoped is repeated rather than inherited: the scope annotation is @Inherited, but in
// an implicit bean archive only a class carrying a bean-defining annotation of its own is
// discovered. See AbstractRepository's "Writing a subclass".
@ApplicationScoped
public class PetRepository extends AbstractRepository<Pet, Long> {

    public PetRepository() {
        super(Pet.class);
    }

    /**
     * The public gallery query: pets matching the filter, newest first.
     *
     * <p>Returns <strong>{@code AVAILABLE} pets only</strong>, whatever the filter says. Each pet
     * arrives with its category and images loaded.
     *
     * @param filter the criteria; any subset may be null, and a {@code null} filter is treated as
     *               {@link PetFilter#none()}
     * @return the matching pets ordered {@code created_at DESC}; empty when none match, never
     *         {@code null}
     */
    public List<Pet> findByFilter(PetFilter filter) {
        return findPets(filter, true, null);
    }

    /**
     * Loads one pet for the details page.
     *
     * <p>Returns a pet of <strong>any status</strong> — the caller decides whether an
     * {@code ADOPTED} or {@code REMOVED} listing may be shown. Category, owner and all images are
     * fetched in the same query, so they stay readable after the {@code EntityManager} closes.
     *
     * @param id the pet's id, may be {@code null}
     * @return the pet, or empty for an unknown or {@code null} id; never {@code null}
     */
    public Optional<Pet> findDetailById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        CriteriaBuilder builder = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Pet> query = builder.createQuery(Pet.class);
        Root<Pet> pet = query.from(Pet.class);

        pet.fetch("category", JoinType.LEFT);
        pet.fetch("owner", JoinType.LEFT);
        pet.fetch("images", JoinType.LEFT);

        query.select(pet)
                .distinct(true)
                .where(builder.equal(pet.get("petId"), id));

        return getEntityManager().createQuery(query).getResultStream().findFirst();
    }

    /**
     * The owner's dashboard query (T-32): everything this user has ever listed.
     *
     * <p>Returns <strong>all statuses</strong>, including {@code ADOPTED} and {@code REMOVED} — an
     * owner has to see the listings that left the gallery. Category and images are loaded, as the
     * dashboard renders the same cards the gallery does.
     *
     * @param ownerId the owner's user id, may be {@code null}
     * @return that owner's pets ordered {@code created_at DESC}; empty for a {@code null} or
     *         unknown id, never {@code null}
     */
    public List<Pet> findByOwnerId(Long ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        return findPets(PetFilter.none(), false, ownerId);
    }

    /**
     * The administration listing (T-34): {@link #findByFilter(PetFilter)} without the status
     * restriction.
     *
     * <p>Returns <strong>all statuses</strong>. This is the only listing that sees {@code ADOPTED}
     * and {@code REMOVED} pets, so it must never back a public screen.
     *
     * @param filter the criteria; any subset may be null, and a {@code null} filter is treated as
     *               {@link PetFilter#none()}
     * @return the matching pets ordered {@code created_at DESC}; empty when none match, never
     *         {@code null}
     */
    public List<Pet> findAllForAdmin(PetFilter filter) {
        return findPets(filter, false, null);
    }

    /**
     * The one query the three listing methods share.
     *
     * @param filter        the criteria; {@code null} is treated as {@link PetFilter#none()}
     * @param availableOnly {@code true} to restrict to {@code AVAILABLE}, as the public gallery does
     * @param ownerId       restrict to this owner, or {@code null} for every owner
     */
    private List<Pet> findPets(PetFilter filter, boolean availableOnly, Long ownerId) {
        PetFilter criteria = filter != null ? filter : PetFilter.none();

        CriteriaBuilder builder = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Pet> query = builder.createQuery(Pet.class);
        Root<Pet> pet = query.from(Pet.class);

        pet.fetch("category", JoinType.LEFT);
        pet.fetch("images", JoinType.LEFT);

        // One predicate per set criterion, combined with AND. An unset criterion adds nothing,
        // so an empty filter leaves only the status and owner restrictions.
        List<Predicate> predicates = new ArrayList<>();
        if (availableOnly) {
            predicates.add(builder.equal(pet.get("status"), Pet.PetStatus.AVAILABLE));
        }
        if (ownerId != null) {
            predicates.add(builder.equal(pet.get("owner").get("userId"), ownerId));
        }
        if (criteria.getCategoryId() != null) {
            predicates.add(builder.equal(pet.get("category").get("categoryId"), criteria.getCategoryId()));
        }
        if (criteria.getSize() != null) {
            predicates.add(builder.equal(pet.get("size"), criteria.getSize()));
        }
        if (criteria.getGender() != null) {
            predicates.add(builder.equal(pet.get("gender"), criteria.getGender()));
        }

        query.select(pet)
                .distinct(true)
                .where(builder.and(predicates.toArray(new Predicate[0])))
                .orderBy(builder.desc(pet.get("createdAt")));

        return getEntityManager().createQuery(query).getResultList();
    }
}
