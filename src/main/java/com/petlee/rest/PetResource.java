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
import java.util.Locale;

/**
 * {@code /api/pets} — the five endpoints at the centre of {@code api-contract.md}, plus
 * {@code GET /api/pets/mine} (ADR-002 deviation #13), which T-32's dashboard needs and the frozen
 * contract has no equivalent of.
 *
 * <h2>No rule is decided here</h2>
 * Ownership, the category check, the optimistic-lock conflict: all of them are
 * {@code PetService}'s, and every status other than 200/204 arrives through T-19 from an exception
 * it threw. What this class does is decide <em>who is asking</em> — from the session, never from
 * the body — turn three query strings into typed values, and map the entity {@code PetService}
 * hands back onto the record the wire carries.
 *
 * <h2>The contact-gating rule lives here now</h2>
 * {@code PetService.findDetail} returns the full entity, owner attached, unconditionally.
 * {@link #findDetail(Long)} is what decides whether the caller is allowed to see the owner's
 * contact details — specification §6 — by passing {@code CurrentUser.from(request).isPresent()}
 * into {@link PetDetailDTO#of(Pet, boolean)}. The JSF tier makes the same decision independently,
 * in {@code PetDetailBean}, because a record cannot be bound into a Facelets view.
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
     * @param categoryId the category to restrict to, or absent for all
     * @param size       {@code SMALL}, {@code MEDIUM} or {@code LARGE}, or absent
     * @param gender     {@code MALE} or {@code FEMALE}, or absent
     * @return the matching pets, newest first, excluding anything not {@code AVAILABLE}
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
     * {@code GET /api/pets/{id}} — <em>open*</em>.
     *
     * <p>The contract's footnote is the whole point of this method:
     * <em>"*Owner contact fields are filled ONLY if the caller is logged in; otherwise null."</em>
     * So it carries no {@code @Secured} — a guest is allowed through, and only the content changes
     * — and the single argument that changes it is whether {@link CurrentUser#from(HttpServletRequest)}
     * finds a session. {@link PetDetailDTO#of(Pet, boolean)} decides what that means, which is
     * specification §6's privacy rule for this tier.
     *
     * @param id the pet id
     * @return the pet in full, with owner contact details only for a logged-in caller
     */
    @GET
    @Path("{id: \\d+}")
    @Produces(MediaType.APPLICATION_JSON)
    public PetDetailDTO findDetail(@PathParam("id") Long id) {
        return PetDetailDTO.of(pets.findDetail(id), CurrentUser.from(request).isPresent());
    }

    /**
     * {@code GET /api/pets/mine} — auth. The caller's own listings, every status included.
     *
     * <p><strong>This endpoint is not in {@code api-contract.md}.</strong> It extends the frozen
     * contract and is recorded as deviation #13 in ADR-002.
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
        return pets.findByOwner(caller().userId()).stream().map(PetDTO::of).toList();
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
        return PetDTO.of(pets.create(form, caller().userId()));
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
        return PetDTO.of(pets.update(id, form, caller().userId()));
    }

    /**
     * {@code DELETE /api/pets/{id}} — owner or admin.
     *
     * <p>The contract's original wording — <em>"Response 204: no body. All images
     * cascade-deleted."</em> — assumed the image gallery this project no longer has; a pet now
     * carries one photograph, and {@code PetService.delete} removes its file along with the row.
     * <em>"Errors: 403 if not owner and not admin"</em> still holds.
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
        pets.delete(id, caller.userId(), caller.admin());
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

    /**
     * The two query-parameter conversions this class and {@link AdminResource} share.
     *
     * <h2>Why not {@code @QueryParam("size") Pet.PetSize}</h2>
     * Jakarta REST answers a conversion failure on a {@code @QueryParam} with <strong>404</strong>,
     * which for {@code ?size=HUGE} would say the collection does not exist — the caller would look
     * for a routing problem and never find the typo. Converting by hand makes it a 400 that names
     * the field and lists the values. Package-private so {@link AdminResource} — the only other
     * caller — can reuse them rather than duplicating the parse.
     */
    static Pet.PetSize parseSize(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Pet.PetSize.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new AppException(400, "size must be one of SMALL, MEDIUM, LARGE");
        }
    }

    static Pet.PetGender parseGender(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Pet.PetGender.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new AppException(400, "gender must be one of MALE, FEMALE");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        // An absent parameter and "?size=" mean the same thing: no restriction.
        return trimmed.isEmpty() ? null : trimmed;
    }
}
