package com.petlee.rest;

import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetForm;
import com.petlee.model.Pet;
import com.petlee.rest.security.CurrentUser;
import com.petlee.rest.security.Secured;
import com.petlee.rest.security.SessionUser;
import com.petlee.service.AppException;
import com.petlee.service.PetService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /api/pets}. Decides who is asking — from the bearer token or the session, never from the
 * body — and maps entities onto records; every rule and every error status belongs to
 * {@link PetService}.
 */
@Path("pets")
@RequestScoped
public class PetResource {

    private PetService pets;

    private CurrentUser currentUser;

    /**
     * For the Authorization header and the session, which Jakarta REST has none of its own.
     */
    @Context
    private HttpServletRequest request;

    /**
     * For the container. Public, as Jakarta REST requires of a root resource class.
     */
    public PetResource() {
    }

    @Inject
    public PetResource(PetService pets, CurrentUser currentUser) {
        this.pets = pets;
        this.currentUser = currentUser;
    }

    /**
     * {@code GET /api/pets} — open. The public gallery.
     *
     * @param categoryId the category to restrict to, or absent for all
     * @param size       SMALL, MEDIUM or LARGE, or absent
     * @param gender     MALE or FEMALE, or absent
     * @return the matching available pets, newest first
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<PetDTO> findGallery(@QueryParam("categoryId") Integer categoryId,
                                    @QueryParam("size") String size,
                                    @QueryParam("gender") String gender) {
        return pets.findGallery(categoryId, parseSize(size), parseGender(gender))
                .stream().map(PetDTO::of).toList();
    }

    /**
     * {@code GET /api/pets/{id}} — open, but the owner's contact fields are filled only for a
     * logged-in caller, and {@code ownedByCaller} is true only for the owner.
     *
     * @param id the pet id
     * @return the pet in full
     */
    @GET
    @Path("{id: \\d+}")
    @Produces(MediaType.APPLICATION_JSON)
    public PetDetailDTO findDetail(@PathParam("id") Long id) {
        Pet pet = pets.findDetail(id);
        Optional<SessionUser> caller = currentUser.from(request);
        boolean owned = caller.isPresent() && pet.getOwner() != null
                && caller.get().userId().equals(pet.getOwner().getUserId());
        return PetDetailDTO.of(pet, caller.isPresent(), owned);
    }

    /**
     * {@code GET /api/pets/mine} — auth. An extension to the contract, for the dashboard.
     * The owner is the session user and never a parameter.
     *
     * @return this user's listings, newest first, in every status
     */
    @GET
    @Path("mine")
    @Secured
    @Produces(MediaType.APPLICATION_JSON)
    public List<PetDTO> findMine() {
        return pets.findByOwner(caller().userId()).stream().map(PetDTO::of).toList();
    }

    /**
     * {@code POST /api/pets} — auth. Answers 200, not 201: the contract is frozen.
     *
     * @param form the listing to create
     * @return the created listing
     */
    @POST
    @Secured
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PetDTO create(PetForm form) {
        if (form == null) {
            throw new AppException(400, "A request body is required");
        }
        return PetDTO.of(pets.create(form, caller().userId()));
    }

    /**
     * {@code PUT /api/pets/{id}?version=N} — auth, owner only.
     *
     * <p>{@code N} is the {@code version} this client read from {@code GET /api/pets/{id}}.
     * A stale or missing version is answered 409, so one client cannot silently overwrite
     * another's change.
     *
     * @param id      the pet to update
     * @param version the version the client last read, as a query parameter
     * @param form    the new values
     * @return the updated listing
     */
    @PUT
    @Path("{id: \\d+}")
    @Secured
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PetDTO update(@PathParam("id") Long id, @QueryParam("version") String version,
                         PetForm form) {
        if (form == null) {
            throw new AppException(400, "A request body is required");
        }
        return PetDTO.of(pets.update(id, form, parseVersion(version), caller().userId()));
    }

    /**
     * Parses the version by hand, for the same reason as {@link #parseFilter}: a failed
     * {@code @QueryParam} conversion is a 404, where a 400 naming the field is what helps.
     *
     * @param value the raw query parameter, may be null or blank
     * @return the version, or null when absent — which the service refuses with 409
     * @throws AppException 400 if the value is present but not a whole number
     */
    private static Long parseVersion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException notANumber) {
            throw new AppException(400, "version must be a whole number");
        }
    }

    /**
     * {@code DELETE /api/pets/{id}} — owner or admin. The photograph goes with the row.
     *
     * @param id the pet to remove
     * @return 204 No Content
     */
    @DELETE
    @Path("{id: \\d+}")
    @Secured
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") Long id) {
        SessionUser caller = caller();
        pets.delete(id, caller.userId(), caller.admin());
        return Response.noContent().build();
    }

    /**
     * {@code POST /api/pets/{id}/image} — auth, owner only. Replaces the listing's photograph
     * with the multipart/form-data part named {@code file}: JPEG, PNG, GIF or WebP, at most
     * 5 MB. The old file is deleted once the new one is attached.
     *
     * @param id   the pet
     * @param file the {@code file} part, or null when the request has none
     * @return the listing, with its new {@code imageUrl}
     */
    @POST
    @Path("{id: \\d+}/image")
    @Secured
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public PetDTO uploadImage(@PathParam("id") Long id, @FormParam("file") EntityPart file) {
        if (file == null) {
            throw new AppException(400, "A photo file is required, as the multipart part \"file\"");
        }
        MediaType type = file.getMediaType();
        String contentType = type == null ? null : type.getType() + "/" + type.getSubtype();

        InputStream content = file.getContent();
        try {
            return PetDTO.of(pets.attachImage(id, content, contentType, caller().userId()));
        } finally {
            try {
                content.close();
            } catch (IOException ignored) {
                // the photograph is stored or refused by now; a failed close changes neither
            }
        }
    }

    /**
     * @return the caller, which {@code @Secured} has already guaranteed exists
     */
    private SessionUser caller() {
        return currentUser.from(request).orElseThrow(() -> new IllegalStateException(
                "no caller on a @Secured endpoint; the annotation is missing or the filter is not bound"));
    }

    static Pet.PetSize parseSize(String value) {
        return parseFilter(Pet.PetSize.class, value, "size must be one of SMALL, MEDIUM, LARGE");
    }

    static Pet.PetGender parseGender(String value) {
        return parseFilter(Pet.PetGender.class, value, "gender must be one of MALE, FEMALE");
    }

    /**
     * Parses an optional enum filter by hand, because Jakarta REST answers a {@code @QueryParam}
     * conversion failure with 404 where a 400 naming the field is what the caller needs. An
     * absent parameter and {@code ?size=} mean the same thing: no restriction.
     *
     * @param type      the enum to parse into
     * @param value     the raw query parameter, may be null or blank
     * @param onUnknown the message for a value that is not one of the constants
     * @return the parsed constant, or null for no restriction
     * @throws AppException 400 if the value is present but unrecognised
     */
    private static <E extends Enum<E>> E parseFilter(Class<E> type, String value, String onUnknown) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new AppException(400, onUnknown);
        }
    }
}
