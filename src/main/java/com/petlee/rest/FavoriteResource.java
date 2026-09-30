package com.petlee.rest;

import com.petlee.dto.PetDTO;
import com.petlee.rest.security.CurrentUser;
import com.petlee.rest.security.Secured;
import com.petlee.rest.security.SessionUser;
import com.petlee.service.FavoriteService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * {@code /api/favorites}: the caller's own saved pets. Every method is {@code @Secured}, and the
 * member is always whoever the token belongs to, so no one can read or change another's list.
 * PUT and DELETE are idempotent, which is why they are PUT and DELETE rather than POST.
 */
@Path("favorites")
@RequestScoped
public class FavoriteResource {

    private FavoriteService favorites;

    private CurrentUser currentUser;

    @Context
    private HttpServletRequest request;

    /** For the container. Public, as Jakarta REST requires of a root resource class. */
    public FavoriteResource() {
    }

    @Inject
    public FavoriteResource(FavoriteService favorites, CurrentUser currentUser) {
        this.favorites = favorites;
        this.currentUser = currentUser;
    }

    /**
     * {@code GET /api/favorites} — auth.
     *
     * @return the caller's saved pets, newest saved first, each with its {@code status}; withdrawn
     *         pets are left out, adopted ones included
     */
    @GET
    @Secured
    @Produces(MediaType.APPLICATION_JSON)
    public List<PetDTO> list() {
        return favorites.list(callerId()).stream().map(PetDTO::of).toList();
    }

    /**
     * {@code GET /api/favorites/ids} — auth. For marking hearts: one call per page, not per pet.
     *
     * @return the ids of the caller's saved pets
     */
    @GET
    @Secured
    @Path("ids")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Long> ids() {
        return List.copyOf(favorites.ids(callerId()));
    }

    /**
     * {@code PUT /api/favorites/{petId}} — auth. Saving a pet already saved is a success too.
     *
     * @param petId the pet to save
     * @return 204 No Content; 404 if there is no such pet or it is not available
     */
    @PUT
    @Secured
    @Path("{petId: \\d+}")
    public Response add(@PathParam("petId") Long petId) {
        favorites.add(callerId(), petId);
        return Response.noContent().build();
    }

    /**
     * {@code DELETE /api/favorites/{petId}} — auth. Removing one that is not saved is a success.
     *
     * @param petId the pet to forget
     * @return 204 No Content
     */
    @DELETE
    @Secured
    @Path("{petId: \\d+}")
    public Response remove(@PathParam("petId") Long petId) {
        favorites.remove(callerId(), petId);
        return Response.noContent().build();
    }

    private Long callerId() {
        SessionUser caller = currentUser.from(request).orElseThrow(() -> new IllegalStateException(
                "no caller on a @Secured endpoint; the annotation is missing or the filter is not bound"));
        return caller.userId();
    }
}
