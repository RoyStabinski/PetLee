package com.petlee.rest;

import com.petlee.dto.CategoryDTO;
import com.petlee.exception.ValidationException;
import com.petlee.rest.security.AdminOnly;
import com.petlee.service.CategoryService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * {@code /api/categories} — the fixed vocabulary the gallery filter and the add-pet form both
 * read.
 *
 * <p>Open, with no {@code @Secured}: specification §10's first scenario is a guest browsing and
 * filtering the gallery, and a filter panel cannot be drawn without this list. There is nothing
 * private in it — six names that are the same for everyone.
 *
 * <p>The write methods are {@code @AdminOnly} (T-34). Specification §5 requires that every pet
 * belongs to a <em>predefined</em> category, so the vocabulary is not something a member extends —
 * only the administrator who is responsible for it. They live in this class rather than under
 * {@code /api/admin} because they are the same collection: a category created here is the one
 * {@code GET /api/categories} returns, and splitting the paths would suggest otherwise.
 */
@Path("categories")
@RequestScoped
public class CategoryResource {

    private CategoryService categories;

    /**
     * For the container. It is {@code public}, not {@code protected}: CDI only needs something it
     * can proxy, but the Jakarta REST specification requires a root resource class to have a
     * public constructor, and RESTEasy enforces it — a protected one deploys on Payara and fails
     * on WildFly with "could not find constructor for class".
     */
    public CategoryResource() {
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
        return categories.findAll().stream().map(CategoryDTO::of).toList();
    }

    /**
     * {@code POST /api/categories} — admin. Body {@code {"name":"Birds"}}.
     *
     * <p>200 with the created category, matching the shape every other create in the contract uses
     * ({@code POST /api/pets} answers 200, not 201). A duplicate name is 409 {@code CATEGORY_EXISTS}
     * from {@code CategoryService}; nothing is decided here.
     *
     * @param body the new category; only {@code name} is read, and an {@code id} in the body is
     *             ignored — the database assigns it
     * @return the created category
     */
    @POST
    @AdminOnly
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CategoryDTO create(CategoryDTO body) {
        if (body == null) {
            throw new ValidationException("name", "NAME_INVALID", "A category name is required");
        }
        return CategoryDTO.of(categories.create(body.name()));
    }

    /**
     * {@code DELETE /api/categories/{id}} — admin.
     *
     * <p>204 on success; 409 {@code CATEGORY_IN_USE} when listings still reference it, which is the
     * readable form of T-03's {@code ON DELETE RESTRICT}.
     *
     * @param id the category to remove
     * @return {@code 204 No Content}
     */
    @DELETE
    @Path("{id: \\d+}")
    @AdminOnly
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") Integer id) {
        categories.delete(id);
        return Response.noContent().build();
    }
}
