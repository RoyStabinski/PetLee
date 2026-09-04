package com.petlee.repository;

import com.petlee.model.Category;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Persistence operations for the fixed category vocabulary every pet listing must reference
 * (specification §5). The rows themselves come from {@code seed.sql}; this class only reads them,
 * plus whatever {@link AbstractRepository} already provides for T-34's admin write paths.
 *
 * <h2>Case sensitivity</h2>
 * Name lookups are case-insensitive, matched as {@code LOWER(c.categoryName) = LOWER(:name)}.
 * A category is a label chosen by an administrator, and {@code Dogs} and {@code dogs} would be the
 * same label to every user looking at the dropdown.
 *
 * <p>The database agrees: {@code ux_category_name_lower} is a unique index on
 * {@code LOWER(category_name)}, so {@link #existsByName(String)} is exactly as strict as the
 * constraint behind it. T-34's check is not the only guarantee — two admin requests racing past it
 * still lose at the database.
 *
 * <h2>Every query is a literal with named parameters</h2>
 * The four queries below are string literals declared at their call site, and every caller-supplied
 * value arrives through {@code setParameter}. Nothing is concatenated into JPQL.
 */
// @ApplicationScoped is repeated rather than inherited: the scope annotation is @Inherited, but in
// an implicit bean archive only a class carrying a bean-defining annotation of its own is
// discovered. See AbstractRepository's "Writing a subclass".
@ApplicationScoped
public class CategoryRepository extends AbstractRepository<Category, Integer> {

    public CategoryRepository() {
        super(Category.class);
    }

    /**
     * Every category, sorted by name ascending.
     *
     * <p>The order is part of the contract rather than a convenience: {@code GET /api/categories}
     * renders a filter dropdown, and an unordered query would let the provider reshuffle the list
     * between requests, moving options under the user's cursor.
     *
     * @return the categories, alphabetically; an empty list if the table is empty, never
     *         {@code null}
     */
    public List<Category> findAllOrderedByName() {
        return getEntityManager()
                .createQuery("SELECT c FROM Category c ORDER BY c.categoryName ASC", Category.class)
                .getResultList();
    }

    /**
     * Finds the category with this name, ignoring case.
     *
     * <p>A missing category gives {@link Optional#empty()}; a {@code null} argument does the same
     * without a query. At most one row can match, since {@code ux_category_name_lower} is unique;
     * the result is still read as the first of a stream rather than through
     * {@code getSingleResult()}, which turns "no row" into an exception instead of an empty
     * result.
     *
     * @param name the category name, in any case, may be {@code null}
     * @return the category, or empty when no such name exists; never {@code null}
     */
    public Optional<Category> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return getEntityManager()
                .createQuery("SELECT c FROM Category c WHERE LOWER(c.categoryName) = LOWER(:name)",
                        Category.class)
                .setParameter("name", name)
                .getResultStream()
                .findFirst();
    }

    /**
     * Reports whether a category with this name already exists, ignoring case.
     *
     * <p>Counts rather than loading the entity: T-34 calls this to reject a duplicate before the
     * insert, and has no use for the row it would otherwise materialise.
     *
     * @param name the category name, in any case, may be {@code null}
     * @return {@code true} when the name is taken; {@code false} for a {@code null} argument
     */
    public boolean existsByName(String name) {
        if (name == null) {
            return false;
        }
        Long matches = getEntityManager()
                .createQuery("SELECT COUNT(c) FROM Category c WHERE LOWER(c.categoryName) = LOWER(:name)",
                        Long.class)
                .setParameter("name", name)
                .getSingleResult();
        return matches > 0;
    }

    /**
     * Counts the pets referencing this category.
     *
     * <p>T-34 calls this before deleting a category, so it can answer {@code 409} with a message an
     * administrator can act on instead of letting T-03's {@code ON DELETE RESTRICT} surface as a
     * raw foreign-key violation.
     *
     * <p><strong>Every</strong> referencing pet is counted, including ones whose status is
     * {@code REMOVED}. That is deliberate and not an oversight: a {@code REMOVED} listing is still a
     * row holding the foreign key, so it would still block the delete. Counting only
     * {@code AVAILABLE} pets would report zero and promise a deletion the database then refuses.
     *
     * @param categoryId the category's id, may be {@code null}
     * @return how many pets reference it; {@code 0} for a {@code null} argument or an unknown id
     */
    public long countPetsInCategory(Integer categoryId) {
        if (categoryId == null) {
            return 0L;
        }
        return getEntityManager()
                .createQuery("SELECT COUNT(p) FROM Pet p WHERE p.category.categoryId = :categoryId",
                        Long.class)
                .setParameter("categoryId", categoryId)
                .getSingleResult();
    }
}
