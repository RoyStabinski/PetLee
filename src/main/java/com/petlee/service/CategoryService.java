package com.petlee.service;

import com.petlee.dto.CategoryDTO;
import com.petlee.exception.ConflictException;
import com.petlee.exception.NotFoundException;
import com.petlee.exception.ValidationException;
import com.petlee.mapper.CategoryMapper;
import com.petlee.model.Category;
import com.petlee.repository.CategoryRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * The category vocabulary — {@code GET /api/categories}, and the lookup every pet creation goes
 * through.
 *
 * <p>Specification §5 requires that "every posted pet must belong to a predefined category".
 * {@link #requireById(Integer)} is where that rule is actually enforced: T-15 calls it for every
 * create and update, so an unknown {@code categoryId} becomes a 404 the caller can read instead of
 * a foreign-key violation surfacing as a 500.
 *
 * <p>Nothing here is cached. The list is six rows read rarely; a cache would buy nothing and
 * would eventually be the reason a category added by T-34 fails to appear.
 */
@ApplicationScoped
public class CategoryService {

    /** {@code category_name} is {@code VARCHAR(50)} in T-03's schema. */
    private static final int NAME_MAX = 50;

    private CategoryRepository categories;

    /** For CDI only — an {@code @ApplicationScoped} proxy needs a no-argument constructor. */
    protected CategoryService() {
    }

    @Inject
    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    /**
     * The whole vocabulary, alphabetically — the body of {@code GET /api/categories}.
     *
     * @return every category as a DTO, ordered by name; empty if the table is empty, never
     *         {@code null}
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<CategoryDTO> findAll() {
        return CategoryMapper.toDtoList(categories.findAllOrderedByName());
    }

    /**
     * One category, for a REST caller.
     *
     * @param id the category id, may be {@code null}
     * @return the category as a DTO
     * @throws NotFoundException <strong>404</strong> — no category has that id
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public CategoryDTO findById(Integer id) {
        return CategoryMapper.toDto(requireById(id));
    }

    /**
     * Adds a category — {@code POST /api/categories}, administrators only (T-34).
     *
     * <p>Duplicates are rejected before the insert so the caller gets a 409 it can act on rather
     * than a constraint violation surfacing as a 500. The check is not the guarantee: two admins
     * racing past it both reach {@code ux_category_name_lower}, and the loser's insert is refused
     * by the database. Names are compared case-insensitively, because {@code Dogs} and {@code dogs}
     * are the same entry in the same dropdown.
     *
     * @param name the new category's name
     * @return the created category
     * @throws ValidationException <strong>400</strong> — the name is blank or over 50 characters,
     *         which is the column's width
     * @throws ConflictException <strong>409</strong>, code {@code CATEGORY_EXISTS}
     */
    @Transactional
    public CategoryDTO create(String name) {
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() > NAME_MAX) {
            throw new ValidationException("name", "NAME_INVALID",
                    "A category name is required, of at most " + NAME_MAX + " characters");
        }
        if (categories.existsByName(trimmed)) {
            throw new ConflictException("CATEGORY_EXISTS", "A category named " + trimmed
                    + " already exists");
        }
        return CategoryMapper.toDto(categories.save(new Category(trimmed)));
    }

    /**
     * Removes a category — {@code DELETE /api/categories/{id}}, administrators only (T-34).
     *
     * <p>A category still holding listings is refused here, with a message naming the count.
     * Specification §5 requires every pet to belong to a category, and T-03's foreign key is
     * {@code ON DELETE RESTRICT}, so without this check the administrator would see a raw 500 for
     * a rule the application knows perfectly well.
     *
     * @param id the category to remove
     * @throws NotFoundException <strong>404</strong> — no category has that id
     * @throws ConflictException <strong>409</strong>, code {@code CATEGORY_IN_USE} — listings still
     *         reference it, {@code REMOVED} ones included: they hold the foreign key too
     */
    @Transactional
    public void delete(Integer id) {
        Category category = requireById(id);

        long listings = categories.countPetsInCategory(id);
        if (listings > 0) {
            throw new ConflictException("CATEGORY_IN_USE", "The category " + category.getCategoryName()
                    + " still holds " + listings + " listing(s), so it cannot be deleted");
        }
        categories.delete(category);
    }

    /**
     * The category as a <strong>managed entity</strong>, or a 404.
     *
     * <h2>Why this one method breaks the "DTOs only" rule</h2>
     * T-15 has to attach a {@link Category} to a {@link com.petlee.model.Pet} before saving it, and
     * a {@code Pet} needs the entity, not a DTO. Handing back the instance this method already
     * loaded avoids a second round trip to re-find it, and — more to the point — avoids T-15
     * reaching into {@link CategoryRepository} itself, which would put the "must be a predefined
     * category" rule in two places.
     *
     * <p>It is <strong>package-private</strong>, and that is the enforcement. Services live in
     * {@code com.petlee.service} and REST resources do not, so no resource class can call it and
     * no entity can escape to the wire through this door. Widening it to {@code public} would undo
     * the guarantee, which is why the visibility is part of T-14's acceptance criteria rather than
     * a matter of style.
     *
     * @param id the category id, may be {@code null}
     * @return the managed category, ready to be set on a {@code Pet}
     * @throws NotFoundException <strong>404</strong>, code {@code CATEGORY_NOT_FOUND} — specification
     *         §5: "Every posted pet must belong to a predefined category"
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    Category requireById(Integer id) {
        return categories.findById(id)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND",
                        "No such category: " + id));
    }
}
