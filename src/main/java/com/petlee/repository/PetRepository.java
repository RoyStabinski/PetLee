package com.petlee.repository;

import com.petlee.model.Pet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class PetRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    /**
     * The gallery query. LEFT JOIN FETCH on category, owner and images is load-bearing: the
     * result is detached when the service transaction ends, the views read all three, and one
     * query for the list beats one more per pet. DISTINCT folds the image rows back into one pet
     * each.
     *
     * <p>Every filter is a named parameter; no value is ever concatenated into the JPQL. An age
     * bound excludes listings whose age is unknown, since SQL compares null as unknown.
     *
     * @param minAge the youngest age to include, or null for no lower bound
     * @param maxAge the oldest age to include, or null for no upper bound
     */
    public List<Pet> find(Integer categoryId, Pet.PetSize size, Pet.PetGender gender,
                          Integer minAge, Integer maxAge, Long ownerId, boolean availableOnly) {
        StringBuilder jpql = new StringBuilder(
                "SELECT DISTINCT p FROM Pet p"
                + " LEFT JOIN FETCH p.category LEFT JOIN FETCH p.owner LEFT JOIN FETCH p.images"
                + " WHERE 1 = 1");
        List<Object[]> params = new ArrayList<>();
        if (availableOnly) {
            jpql.append(" AND p.status = :status");
            params.add(new Object[]{"status", Pet.PetStatus.AVAILABLE});
        }
        if (ownerId != null) {
            jpql.append(" AND p.owner.userId = :ownerId");
            params.add(new Object[]{"ownerId", ownerId});
        }
        if (categoryId != null) {
            jpql.append(" AND p.category.categoryId = :categoryId");
            params.add(new Object[]{"categoryId", categoryId});
        }
        if (size != null) {
            jpql.append(" AND p.size = :size");
            params.add(new Object[]{"size", size});
        }
        if (gender != null) {
            jpql.append(" AND p.gender = :gender");
            params.add(new Object[]{"gender", gender});
        }
        if (minAge != null) {
            jpql.append(" AND p.age >= :minAge");
            params.add(new Object[]{"minAge", minAge});
        }
        if (maxAge != null) {
            jpql.append(" AND p.age <= :maxAge");
            params.add(new Object[]{"maxAge", maxAge});
        }
        jpql.append(" ORDER BY p.createdAt DESC");

        TypedQuery<Pet> query = em.createQuery(jpql.toString(), Pet.class);
        params.forEach(p -> query.setParameter((String) p[0], p[1]));
        return query.getResultList();
    }

    public Optional<Pet> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return em.createQuery(
                        "SELECT DISTINCT p FROM Pet p LEFT JOIN FETCH p.category"
                        + " LEFT JOIN FETCH p.owner LEFT JOIN FETCH p.images"
                        + " WHERE p.petId = :id", Pet.class)
                .setParameter("id", id)
                .getResultStream().findFirst();
    }

    /**
     * Loads a pet with {@code SELECT ... FOR UPDATE}, without its images, so image changes for
     * one pet run one at a time: two uploads cannot both pass the five-image check, and two
     * first uploads cannot both become main. The lock does not touch the version.
     *
     * @param id the pet's id, may be null
     * @return the locked pet, or empty
     */
    public Optional<Pet> lockById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(Pet.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    /**
     * Counts listings per category in every status, independent of any filter. Categories
     * holding no listings are absent from the map.
     */
    public Map<Integer, Long> countByCategory() {
        return em.createQuery(
                        "SELECT p.category.categoryId, COUNT(p) FROM Pet p GROUP BY p.category.categoryId",
                        Object[].class)
                .getResultStream()
                .collect(Collectors.toMap(row -> (Integer) row[0], row -> (Long) row[1]));
    }

    public Pet save(Pet pet) {
        if (pet.getPetId() == null) {
            em.persist(pet);
            em.flush();
            return pet;
        }
        Pet merged = em.merge(pet);
        em.flush();
        return merged;
    }

    public void delete(Pet pet) {
        em.remove(em.contains(pet) ? pet : em.merge(pet));
        em.flush();
    }
}
