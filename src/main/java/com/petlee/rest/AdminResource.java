package com.petlee.rest;

import com.petlee.dto.PetDTO;
import com.petlee.rest.security.AdminOnly;
import com.petlee.service.PetService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * {@code /api/admin} — the moderation endpoints, an extension to the frozen contract.
 * {@code @AdminOnly} sits on the class, so no method can be added without it.
 */
@Path("admin")
@AdminOnly
@RequestScoped
public class AdminResource {

    private PetService pets;

    /** For the container. Public, as Jakarta REST requires of a root resource class. */
    public AdminResource() {
    }

    @Inject
    public AdminResource(PetService pets) {
        this.pets = pets;
    }

    /**
     * {@code GET /api/admin/pets} — admin. Every listing, in every status, newest first.
     *
     * @param categoryId the category to restrict to, or absent for all
     * @param size       SMALL, MEDIUM or LARGE, or absent
     * @param gender     MALE or FEMALE, or absent
     * @return the matching listings
     */
    @GET
    @Path("pets")
    @Produces(MediaType.APPLICATION_JSON)
    public List<PetDTO> findAll(@QueryParam("categoryId") Integer categoryId,
                                @QueryParam("size") String size,
                                @QueryParam("gender") String gender) {
        return pets.findAllForAdmin(categoryId, PetResource.parseSize(size), PetResource.parseGender(gender))
                .stream().map(PetDTO::of).toList();
    }

    /**
     * {@code PUT /api/admin/pets/{id}/status} — admin. The reversible half of moderation:
     * REMOVED hides a listing, AVAILABLE puts it back, and the row is untouched either way.
     *
     * @param id     the listing to hide or restore
     * @param status the new status
     * @return the listing in its new state
     */
    @PUT
    @Path("pets/{id: \\d+}/status")
    @Produces(MediaType.APPLICATION_JSON)
    public PetDTO changeStatus(@PathParam("id") Long id, @QueryParam("status") String status) {
        return PetDTO.of(pets.changeStatus(id, status));
    }
}
