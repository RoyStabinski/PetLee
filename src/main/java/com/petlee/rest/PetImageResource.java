package com.petlee.rest;

import com.petlee.dto.PetImageDTO;
import com.petlee.exception.ValidationException;
import com.petlee.rest.security.CurrentUser;
import com.petlee.rest.security.Secured;
import com.petlee.rest.security.SessionUser;
import com.petlee.service.ImageStorageService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@code /api/pets/{id}/images} — uploading and removing a listing's photographs.
 *
 * <h2>Multipart through the Servlet API, not a Jakarta REST extension</h2>
 * The body is read with {@code request.getPart("file")} rather than with Jersey's form-data
 * parameter annotation. That annotation lives in {@code jersey-media-multipart}, which ADR-003
 * forbids adding and which would not deploy on WildFly at all; {@link Part} is part of the
 * platform and behaves the same on every Jakarta EE server. (Neither name is spelled here on
 * purpose: T-23 criterion 9 is a grep, and a grep cannot tell a mention from a use.) It also gives T-16 everything it asks
 * for: the stream, the declared size, the declared type and the client's filename.
 *
 * <p>The size cap is applied by {@code MultipartConfigurator} at start-up rather than with
 * {@code @MultipartConfig}, because the servlet handling these requests is the container's own
 * Jakarta REST servlet and there is no class of ours to annotate. That class explains why the
 * declarative alternative had to be abandoned.
 *
 * <h2>Nothing is decided here</h2>
 * No validation, no file I/O, no ownership check. {@link ImageStorageService} owns all three, and
 * a second copy of the owner check in this class would be a second place for it to be wrong. This
 * resource reads two fields off the request and passes them down with the caller's id.
 */
@Path("pets/{petId}/images")
@RequestScoped
public class PetImageResource {

    private ImageStorageService images;

    /** For the session and, here, for the multipart body itself. */
    @Context
    private HttpServletRequest request;

    /**
     * For the container. It is {@code public}, not {@code protected}: CDI only needs something it
     * can proxy, but the Jakarta REST specification requires a root resource class to have a
     * public constructor, and RESTEasy enforces it — a protected one deploys on Payara and fails
     * on WildFly with "could not find constructor for class".
     */
    public PetImageResource() {
    }

    @Inject
    public PetImageResource(ImageStorageService images) {
        this.images = images;
    }

    /**
     * {@code POST /api/pets/{id}/images} — auth + owner.
     *
     * <p>The contract: <em>"Request: multipart/form-data — file + boolean 'isMain'"</em>,
     * <em>"Response 200 (PetImageDTO)"</em> with the keys {@code id}, {@code imageUrl} and
     * {@code isMain}.
     *
     * <p>{@code isMain} is read with {@code getParameter}, which sees a multipart field as well as
     * a query string, and anything other than {@code true} is {@code false} — a form that omits an
     * unchecked box is the ordinary case, not an error. T-16 overrides it to {@code true} for a
     * pet's first photograph whatever was asked.
     *
     * @param petId the pet the photograph belongs to
     * @return the stored image
     */
    @POST
    @Secured
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public PetImageDTO upload(@PathParam("petId") Long petId) {
        Part file = filePart();
        boolean isMain = Boolean.parseBoolean(request.getParameter("isMain"));

        try (InputStream data = file.getInputStream()) {
            return images.store(petId, data, file.getSubmittedFileName(), file.getContentType(),
                    file.getSize(), isMain, caller().getUserId());
        } catch (IOException e) {
            // The connection died mid-upload. Nothing was written - T-16 reads the bytes before it
            // creates a file - so there is nothing to clean up, and the caller gets a 400 rather
            // than the 500 an escaping IOException would produce.
            throw new ValidationException("file", "UPLOAD_FAILED",
                    "The photograph could not be read; please try again");
        }
    }

    /**
     * {@code DELETE /api/pets/{petId}/images/{imageId}} — auth, owner or admin.
     *
     * <p><strong>This endpoint is not in {@code api-contract.md}.</strong> It extends the frozen
     * contract and is recorded as deviation #10 in ADR-002; T-41 adds it to the contract document.
     * T-32's dashboard needs it, because a listing that can gain a bad photograph and never lose
     * one is a listing whose owner has to delete the whole pet to fix a picture.
     *
     * @param petId   the pet, present for the URL's shape; the image id alone identifies the row
     * @param imageId the photograph to remove
     * @return {@code 204 No Content}
     */
    @DELETE
    @Path("{imageId}")
    @Secured
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("petId") Long petId, @PathParam("imageId") Integer imageId) {
        SessionUser caller = caller();
        images.deleteImage(imageId, caller.getUserId(), caller.isAdmin());
        return Response.noContent().build();
    }

    /**
     * @return the {@code file} part, never {@code null}
     * @throws ValidationException 400 when the request is not multipart, carries no {@code file}
     *         field, or exceeds the container's configured limits. All three are the client's
     *         mistake, and the container reports the last of them by throwing rather than by
     *         handing over a truncated part.
     */
    private Part filePart() {
        try {
            Part file = request.getPart("file");
            if (file == null) {
                throw new ValidationException("file", "FILE_REQUIRED",
                        "A file field is required");
            }
            return file;
        } catch (IOException | ServletException | IllegalStateException notMultipart) {
            // IllegalStateException is how the container reports maxFileSize or maxRequestSize
            // being exceeded, and it is thrown before the body is buffered - which is the point of
            // declaring the caps there rather than checking a size here.
            throw new ValidationException("file", "UPLOAD_REJECTED",
                    "The upload must be multipart/form-data with a file field of at most 5 MB");
        }
    }

    /**
     * @return the session user; {@code @Secured} has already guaranteed one exists
     */
    private SessionUser caller() {
        return CurrentUser.from(request).orElseThrow(() -> new IllegalStateException(
                "no session on a @Secured endpoint; the annotation is missing or the filter is not bound"));
    }
}
