package com.petlee.service;

import com.petlee.dto.CategoryDTO;
import com.petlee.exception.NotFoundException;
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
