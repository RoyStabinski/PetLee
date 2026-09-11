package com.petlee.repository;

import com.petlee.model.Pet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PetRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    /**
     * The gallery query. LEFT JOIN FETCH on category and owner is load-bearing: the result
     * is detached when the service transaction ends, and the views read both.
     */
    public List<Pet> find(Integer categoryId, Pet.PetSize size, Pet.PetGender gender,
                          Long ownerId, boolean availableOnly) {
        StringBuilder jpql = new StringBuilder(
                "SELECT DISTINCT p FROM Pet p"
                + " LEFT JOIN FETCH p.category LEFT JOIN FETCH p.owner WHERE 1 = 1");
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
                        "SELECT p FROM Pet p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.owner"
                        + " WHERE p.petId = :id", Pet.class)
                .setParameter("id", id)
                .getResultStream().findFirst();
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
