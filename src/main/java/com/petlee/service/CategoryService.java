package com.petlee.service;

import com.petlee.model.Category;
import com.petlee.repository.CategoryRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.util.List;

/** The category vocabulary, and the lookup every pet creation resolves its category through. */
@ApplicationScoped
public class CategoryService {

    /** The width of the category_name column. */
    private static final int NAME_MAX = 50;

    private CategoryRepository categories;

    /** For CDI only. */
    protected CategoryService() {
    }

    @Inject
    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    /** @return every category, ordered by name; never null */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Category> findAll() {
        return categories.findAll();
    }

    /**
     * Adds a category. Names are compared case-insensitively.
     *
     * @param name the new category's name
     * @return the created category
     * @throws AppException 400 if the name is blank or too long, 409 if it already exists
     */
    @Transactional
    public Category create(String name) {
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() > NAME_MAX) {
            throw new AppException(400,
                    "A category name is required, of at most " + NAME_MAX + " characters");
        }
        // For the message, not the guarantee: ux_category_name_lower decides a race.
        if (categories.existsByName(trimmed)) {
            throw new AppException(409, "A category named " + trimmed + " already exists");
        }
        try {
            return categories.save(new Category(trimmed));
        } catch (PersistenceException race) {
            throw new AppException(409, "A category named " + trimmed + " already exists", race);
        }
    }

    /**
     * Removes a category. One that still holds listings is refused with a readable message
     * rather than a foreign-key error.
     *
     * @param id the category to remove
     * @throws AppException 404 if no such category, 409 if listings still reference it
     */
    @Transactional
    public void delete(Integer id) {
        Category category = requireById(id);

        long listings = categories.countPetsInCategory(id);
        if (listings > 0) {
            throw new AppException(409, "The category " + category.getCategoryName()
                    + " still holds " + listings + " listing(s), so it cannot be deleted");
        }
        categories.delete(category);
    }

    /**
     * Loads a managed category. Package-private, so no entity escapes to the wire this way.
     *
     * @param id the category id, may be null
     * @return the managed category, ready to be set on a Pet
     * @throws AppException 404 if no such category
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    Category requireById(Integer id) {
        return categories.findById(id)
                .orElseThrow(() -> new AppException(404, "No such category: " + id));
    }
}
