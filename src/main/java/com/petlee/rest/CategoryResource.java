package com.petlee.rest;

import com.petlee.dto.CategoryDTO;
import com.petlee.rest.security.AdminOnly;
import com.petlee.service.AppException;
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
 * {@code /api/categories} — the fixed vocabulary the gallery filter and the add-pet form read.
 * Reading is open, since a guest needs the filter panel; writing is the administrator's.
 */
@Path("categories")
@RequestScoped
public class CategoryResource {

    private CategoryService categories;

    /** For the container. Public, as Jakarta REST requires of a root resource class. */
    public CategoryResource() {
    }

    @Inject
    public CategoryResource(CategoryService categories) {
        this.categories = categories;
    }

    /**
     * {@code GET /api/categories} — open. An empty table is 200 and {@code []}, never 204.
     *
     * @return every category, ordered by name
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<CategoryDTO> findAll() {
        return categories.findAll().stream().map(CategoryDTO::of).toList();
    }

    /**
     * {@code POST /api/categories} — admin.
     *
     * @param body the new category; only {@code name} is read, since the database assigns the id
     * @return the created category
     */
    @POST
    @AdminOnly
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CategoryDTO create(CategoryDTO body) {
        if (body == null) {
            throw new AppException(400, "A category name is required");
        }
        return CategoryDTO.of(categories.create(body.name()));
    }

    /**
     * {@code DELETE /api/categories/{id}} — admin. A category still holding listings is a 409.
     *
     * @param id the category to remove
     * @return 204 No Content
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
