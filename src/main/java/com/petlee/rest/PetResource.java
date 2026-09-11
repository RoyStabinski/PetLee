package com.petlee.rest;

import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetForm;
import com.petlee.model.Pet;
import com.petlee.rest.security.CurrentUser;
import com.petlee.rest.security.Secured;
import com.petlee.rest.security.SessionUser;
import com.petlee.service.PetService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * {@code /api/pets} — the five endpoints at the centre of {@code api-contract.md}, plus
 * {@code GET /api/pets/mine} (ADR-002 deviation #11), which T-32's dashboard needs and the frozen
 * contract has no equivalent of.
 *
 * <h2>No rule is decided here</h2>
 * Ownership, the privacy of contact details, the category check, the optimistic-lock conflict:
 * all of them are {@code PetService}'s, and every status other than 200/204 arrives through T-19
 * from an exception it threw. What this class does is decide <em>who is asking</em> — from the
 * session, never from the body — and turn three query strings into typed values.
 *
 * <h2>Where the caller comes from</h2>
 * The session, in every case. {@link PetForm} has no owner field and must not gain one:
 * specification §5 gives each listing exactly one owner, and an owner nameable in the request body
 * is an owner an attacker can choose. For {@code DELETE} both the id and the admin flag are passed
 * down, because the owner-or-admin decision is the service's to make — a resource that decided it
 * would be a second copy of the rule, and the copies would drift.
 */
@Path("pets")
@RequestScoped
public class PetResource {

    private PetService pets;

    /** For the session. See {@link AuthResource} on why Jakarta REST reaches for the Servlet API. */
    @Context
    private HttpServletRequest request;

    /**
     * For the container. It is {@code public}, not {@code protected}: CDI only needs something it
     * can proxy, but the Jakarta REST specification requires a root resource class to have a
     * public constructor, and RESTEasy enforces it — a protected one deploys on Payara and fails
     * on WildFly with "could not find constructor for class".
     */
    public PetResource() {
    }

    @Inject
    public PetResource(PetService pets) {
        this.pets = pets;
    }

    /**
     * {@code GET /api/pets} — open.
     *
     * <p>The contract: <em>"Optional filter params: ?categoryId=1&amp;size=SMALL&amp;gender=MALE"</em>,
     * <em>"Response 200 (List&lt;PetDTO&gt; — gallery view, main image only, newest first)"</em>.
     * An empty catalogue is 200 and {@code []}.
     *
     * <p>The three parameters are taken as strings and converted by {@link RestParams} rather than
     * declared as {@code Integer} and enum types; that class says why.
     *
     * @param categoryId the category to restrict to, or absent for all
     * @param size       {@code SMALL}, {@code MEDIUM} or {@code LARGE}, or absent
     * @param gender     {@code MALE} or {@code FEMALE}, or absent
     * @return the matching pets, newest first, excluding anything not {@code AVAILABLE}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<PetDTO> findGallery(@QueryParam("categoryId") String categoryId,
                                    @QueryParam("size") String size,
                                    @QueryParam("gender") String gender) {
        return pets.findGallery(RestParams.categoryId(categoryId),
                RestParams.enumValue(Pet.PetSize.class, size, "size"),
                RestParams.enumValue(Pet.PetGender.class, gender, "gender"));
    }

    /**
     * {@code GET /api/pets/{id}} — <em>open*</em>.
     *
     * <p>The contract's footnote is the whole point of this method:
     * <em>"*Owner contact fields are filled ONLY if the caller is logged in; otherwise null."</em>
     * So it carries no {@code @Secured} — a guest is allowed through, and only the content changes
     * — and the single argument that changes it is
     * {@link CurrentUser#userIdOrNull(HttpServletRequest)}: {@code null} for a guest, an id for a
     * logged-in caller. {@code PetService.findDetail} decides what that means, which is
     * specification §6's privacy rule in one place rather than in every caller.
     *
     * @param id the pet id
     * @return the pet in full, with owner contact details only for a logged-in caller
     */
    @GET
    @Path("{id: \\d+}")
    @Produces(MediaType.APPLICATION_JSON)
    public PetDetailDTO findDetail(@PathParam("id") Long id) {
        return pets.findDetail(id, CurrentUser.userIdOrNull(request));
    }

    /**
     * {@code GET /api/pets/mine} — auth. The caller's own listings, every status included.
     *
     * <p><strong>This endpoint is not in {@code api-contract.md}.</strong> It extends the frozen
     * contract and is recorded as deviation #11 in ADR-002; T-41 adds it to the contract document.
     * T-32's dashboard needs it and nothing else can supply it: {@code GET /api/pets} is the public
     * gallery, which hides {@code ADOPTED} and {@code REMOVED} listings and returns a
     * {@link PetDTO} with no owner field, so a client could neither see its own hidden listings nor
     * pick its own out of the result.
     *
     * <p>The owner is the session user, never a parameter. An {@code ?ownerId=} would turn the one
     * endpoint that shows a user their withdrawn listings into a way to read anybody's.
     *
     * <p>The path is a literal, so it is matched ahead of {@code {id}} whatever the order of the
     * methods in this file — and {@code {id}} is additionally constrained to digits, so the two can
     * never compete.
     *
     * @return this user's listings, newest first, of every status
     */
    @GET
    @Path("mine")
    @Secured
    @Produces(MediaType.APPLICATION_JSON)
    public List<PetDTO> findMine() {
        return pets.findByOwner(caller().getUserId());
    }

    /**
     * {@code POST /api/pets} — auth.
     *
     * <p>The contract: <em>"Response 200: the created PetDTO"</em>. 200, not 201 — frozen.
     *
     * @param form the listing to create
     * @return the created listing
     */
    @POST
    @Secured
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PetDTO create(PetForm form) {
        return pets.create(form, caller().getUserId());
    }

    /**
     * {@code PUT /api/pets/{id}} — auth + owner.
     *
     * <p>The contract: <em>"Response 200: updated PetDTO"</em>, <em>"Errors: 403 if not the owner.
     * 409 if a concurrent edit happened (optimistic lock)."</em> Both come from
     * {@code PetService.update} through T-19; neither is decided here.
     *
     * @param id   the pet to update
     * @param form the new values
     * @return the updated listing
     */
    @PUT
    @Path("{id: \\d+}")
    @Secured
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PetDTO update(@PathParam("id") Long id, PetForm form) {
        return pets.update(id, form, caller().getUserId());
    }

    /**
     * {@code POST /api/pets/{id}/image} — auth + owner.
     *
     * <p>Deviates from {@code api-contract.md}, which names this endpoint with a plural path and
     * has it answer with a per-image DTO. There is one photo per pet now, so neither means
     * anything; this answers with the updated {@link PetDTO} instead. Recorded in ADR-002.
     *
     * @param id   the pet to attach the photo to
     * @param file the uploaded file
     * @return the pet, with its new {@code mainImageUrl}
     */
    @POST
    @Path("{id: \\d+}/image")
    @Secured
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public PetDTO uploadImage(@PathParam("id") Long id,
                              @FormParam("file") Part file) {
        return pets.attachImage(id, file, caller().getUserId());
    }

    /**
     * {@code DELETE /api/pets/{id}} — owner or admin.
     *
     * <p>The contract: <em>"Response 204: no body. All images cascade-deleted."</em>,
     * <em>"Errors: 403 if not owner and not admin."</em>
     *
     * <p>The admin flag is passed in rather than read inside the service, which is the standing
     * rule: authorisation decisions belong to the service layer, made on arguments, never on a
     * session or a thread-local the service reaches for itself.
     *
     * @param id the pet to remove
     * @return {@code 204 No Content}
     */
    @DELETE
    @Path("{id: \\d+}")
    @Secured
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") Long id) {
        SessionUser caller = caller();
        pets.delete(id, caller.getUserId(), caller.isAdmin());
        return Response.noContent().build();
    }

    /**
     * @return the session user. {@code @Secured} has already guaranteed one exists, so an empty
     *         session here is not a client error to report but a resource method missing its
     *         annotation — the same reasoning as {@code PetService.create}'s null check.
     */
    private SessionUser caller() {
        return CurrentUser.from(request).orElseThrow(() -> new IllegalStateException(
                "no session on a @Secured endpoint; the annotation is missing or the filter is not bound"));
    }
}
