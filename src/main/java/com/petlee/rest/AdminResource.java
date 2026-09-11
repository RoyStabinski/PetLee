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
 * {@code /api/admin} — the moderation endpoints specification §3 asks for and
 * {@code api-contract.md} does not define. An extension, recorded as ADR-002 #12; no existing
 * endpoint changes, so no caller breaks.
 *
 * <h2>Two endpoints, not five</h2>
 * Deleting a listing is already {@code DELETE /api/pets/{id}}, which T-15 permits for the owner
 * <em>or</em> an administrator. A second deletion path would mean two sets of rules to keep in
 * step, so there is not one. There are no user endpoints either: specification §2 gives an
 * administrator authority over listings and categories, not over browsing personal data.
 *
 * <h2>Who may enter</h2>
 * {@code @AdminOnly} on the class, so every method inherits it and none can be added without it.
 * The filter answers 401 to a guest and 403 {@code NOT_ADMIN} to a member, before any method here
 * runs; nothing below re-checks a role.
 */
@Path("admin")
@AdminOnly
@RequestScoped
public class AdminResource {

    private PetService pets;

    /**
     * For the container. Public rather than protected for the same reason as {@link PetResource}'s:
     * the Jakarta REST specification requires a public constructor on a root resource, and RESTEasy
     * enforces it.
     */
    public AdminResource() {
    }

    @Inject
    public AdminResource(PetService pets) {
        this.pets = pets;
    }

    /**
     * {@code GET /api/admin/pets} — admin. Every listing, in every status, newest first.
     *
     * <p>The same three optional filters as {@code GET /api/pets}, parsed the same way, so a bad
     * value is a 400 naming the field rather than a silently unfiltered table. Status is not a
     * query parameter: the table is small enough that T-35 filters it in the browser, and an
     * endpoint that can be asked for one status is an endpoint that can be asked for none.
     *
     * @param categoryId the category to restrict to, or absent for all
     * @param size       {@code SMALL}, {@code MEDIUM} or {@code LARGE}, or absent
     * @param gender     {@code MALE} or {@code FEMALE}, or absent
     * @return the matching listings, mapped to the gallery shape
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
     * {@code PUT /api/admin/pets/{id}/status} — admin.
     *
     * <p>The reversible half of moderation: {@code REMOVED} takes a listing out of the public
     * gallery and {@code AVAILABLE} puts it back, with the row and its photographs untouched.
     * Which values are legal is {@code PetService}'s decision, not this method's. A query
     * parameter rather than a one-field JSON body — ADR-002 #11 — since a whole request body for
     * one string bought nothing.
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
