package com.petlee.repository;

import com.petlee.model.Category;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

/** The categories, and the listing count that guards deleting one. */
@ApplicationScoped
public class CategoryRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    /** @return every category, ordered by name */
    public List<Category> findAll() {
        return em.createQuery(
                "SELECT c FROM Category c ORDER BY c.categoryName", Category.class).getResultList();
    }

    /**
     * @param id the category's id, may be null
     * @return the category, or empty for a null or unknown id
     */
    public Optional<Category> findById(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(em.find(Category.class, id));
    }

    /**
     * Compares with LOWER() on both sides, matching ux_category_name_lower: one label, one
     * category, whatever case it is typed in.
     *
     * @param name the name to look for, may be null
     * @return whether a category of that name exists; false for null
     */
    public boolean existsByName(String name) {
        return name != null && em.createQuery(
                        "SELECT COUNT(c) FROM Category c WHERE LOWER(c.categoryName) = LOWER(:name)",
                        Long.class)
                .setParameter("name", name).getSingleResult() > 0;
    }

    /**
     * @param categoryId the category's id, may be null
     * @return how many listings reference it, in every status; 0 for null
     */
    public long countPetsInCategory(Integer categoryId) {
        if (categoryId == null) {
            return 0L;
        }
        return em.createQuery(
                        "SELECT COUNT(p) FROM Pet p WHERE p.category.categoryId = :categoryId", Long.class)
                .setParameter("categoryId", categoryId)
                .getSingleResult();
    }

    /**
     * Inserts a new category or merges a detached one, and flushes, so a constraint violation
     * surfaces here rather than at commit.
     *
     * @param category the category to save
     * @return the managed category
     */
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

    /**
     * Deletes a category and flushes, so a foreign-key refusal surfaces here.
     *
     * @param category the category, managed or detached
     */
    public void delete(Category category) {
        em.remove(em.contains(category) ? category : em.merge(category));
        em.flush();
    }
}
