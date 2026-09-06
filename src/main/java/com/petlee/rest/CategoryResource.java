package com.petlee.rest;

import com.petlee.dto.CategoryDTO;
import com.petlee.service.CategoryService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * {@code /api/categories} — the fixed vocabulary the gallery filter and the add-pet form both
 * read.
 *
 * <p>Open, with no {@code @Secured}: specification §10's first scenario is a guest browsing and
 * filtering the gallery, and a filter panel cannot be drawn without this list. There is nothing
 * private in it — six names that are the same for everyone.
 *
 * <p>No POST, PUT or DELETE. Specification §5 requires that every pet belongs to a
 * <em>predefined</em> category, so the vocabulary is not something a user extends; T-34 adds the
 * administrator's management endpoints to this same class.
 */
@Path("categories")
@RequestScoped
public class CategoryResource {

    private CategoryService categories;

    /** For CDI, which needs a no-argument constructor to proxy this bean. */
    protected CategoryResource() {
    }

    @Inject
    public CategoryResource(CategoryService categories) {
        this.categories = categories;
    }

    /**
     * {@code GET /api/categories} — open.
     *
     * <p>The contract: <em>"Response 200 (List&lt;CategoryDTO&gt;)"</em>, objects with exactly
     * {@code id} and {@code name}, which is all {@link CategoryDTO} has.
     *
     * <p>An empty table is {@code 200} with {@code []}, never {@code 204} and never {@code null}:
     * a client that has to distinguish "no categories" from "no body" needs two code paths where
     * one will do, and {@code CategoryService.findAll} already promises a list.
     *
     * @return every category, ordered by name
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<CategoryDTO> findAll() {
        return categories.findAll();
    }
}
