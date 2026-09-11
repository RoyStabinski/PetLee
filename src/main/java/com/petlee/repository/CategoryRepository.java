package com.petlee.repository;

import com.petlee.model.Category;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CategoryRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    public List<Category> findAll() {
        return em.createQuery(
                "SELECT c FROM Category c ORDER BY c.categoryName", Category.class).getResultList();
    }

    public Optional<Category> findById(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(em.find(Category.class, id));
    }

    // LOWER() on both sides, matching ux_category_name_lower: one label, one category,
    // whatever case it is typed in.
    public boolean existsByName(String name) {
        return name != null && em.createQuery(
                        "SELECT COUNT(c) FROM Category c WHERE LOWER(c.categoryName) = LOWER(:name)",
                        Long.class)
                .setParameter("name", name).getSingleResult() > 0;
    }

    public long countPetsInCategory(Integer categoryId) {
        if (categoryId == null) {
            return 0L;
        }
        return em.createQuery(
                        "SELECT COUNT(p) FROM Pet p WHERE p.category.categoryId = :categoryId", Long.class)
                .setParameter("categoryId", categoryId)
                .getSingleResult();
    }

    public Category save(Category category) {
        if (category.getCategoryId() == null) {
            em.persist(category);
            em.flush();
            return category;
        }
        Category merged = em.merge(category);
        em.flush();
        return merged;
    }

    public void delete(Category category) {
        em.remove(em.contains(category) ? category : em.merge(category));
        em.flush();
    }
}
