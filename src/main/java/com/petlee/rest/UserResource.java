package com.petlee.rest;

import com.petlee.dto.ChangePasswordForm;
import com.petlee.dto.ProfileForm;
import com.petlee.dto.RegisterForm;
import com.petlee.dto.UserDTO;
import com.petlee.model.User;
import com.petlee.rest.security.CurrentUser;
import com.petlee.rest.security.Secured;
import com.petlee.rest.security.SessionUser;
import com.petlee.rest.security.TokenStore;
import com.petlee.service.AppException;
import com.petlee.service.UserService;

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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@code /api/users} — registration, and the caller's own profile under {@code /me}. Every
 * {@code /me} endpoint acts for whoever the token or session belongs to; none takes a user id,
 * so no member can read or change another's account. Every rule belongs to {@link UserService}
 * and every error status to the exception mapper, so there is deliberately no try/catch here.
 */
@Path("users")
@RequestScoped
public class UserResource {

    private UserService users;

    private CurrentUser currentUser;

    private TokenStore tokens;

    /** For the Authorization header and the session, which Jakarta REST has none of its own. */
    @Context
    private HttpServletRequest request;

    /** For the container. Public, as Jakarta REST requires of a root resource class. */
    public UserResource() {
    }

    @Inject
    public UserResource(UserService users, CurrentUser currentUser, TokenStore tokens) {
        this.users = users;
        this.currentUser = currentUser;
        this.tokens = tokens;
    }

    /**
     * {@code POST /api/users/register} — open. Creates no session; registration does not log
     * anybody in. {@link UserDTO} has no password field, so none can be echoed back.
     *
     * @param form the registration body
     * @return the created user
     */
    @POST
    @Path("register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO register(RegisterForm form) {
        if (form == null) {
            throw new AppException(400, "A request body is required");
        }
        User user = users.register(form);
        return UserDTO.of(user);
    }

    /**
     * {@code GET /api/users/me} — auth.
     *
     * @return the caller's account, with the {@code version} to send back when saving it
     */
    @GET
    @Path("me")
    @Secured
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO me() {
        return UserDTO.of(users.findProfile(caller().userId()));
    }

    /**
     * {@code PUT /api/users/me?version=N} — auth. {@code N} is the {@code version} from
     * {@code GET /api/users/me}; a stale or missing one is 409, as for a pet. The username
     * cannot change.
     *
     * @param version the version the client last read, as a query parameter
     * @param form    the new values
     * @return the updated account
     */
    @PUT
    @Path("me")
    @Secured
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO updateProfile(@QueryParam("version") String version, ProfileForm form) {
        if (form == null) {
            throw new AppException(400, "A request body is required");
        }
        return UserDTO.of(users.updateProfile(caller().userId(), form,
                PetResource.parseVersion(version)));
    }

    /**
     * {@code PUT /api/users/me/password} — auth. On success every other bearer token of this
     * user is revoked, so a device signed in with the old password is signed out; the token
     * this request carried, if any, stays valid.
     *
     * @param form the current and the new password
     * @return 204 No Content
     */
    @PUT
    @Path("me/password")
    @Secured
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changePassword(ChangePasswordForm form) {
        if (form == null) {
            throw new AppException(400, "A request body is required");
        }
        Long userId = caller().userId();
        users.changePassword(userId, form);
        tokens.revokeAllExcept(userId, currentUser.bearerToken(request).orElse(null));
        return Response.noContent().build();
    }

    /**
     * {@code POST /api/users/me/avatar} — auth. Replaces the profile photo with the
     * multipart/form-data part named {@code file}: JPEG, PNG, GIF or WebP, at most 5 MB. The old
     * photo's file is deleted.
     *
     * @param file the {@code file} part, or null when the request has none
     * @return the account with its new {@code avatarUrl}
     */
    @POST
    @Path("me/avatar")
    @Secured
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO setAvatar(@FormParam("file") EntityPart file) {
        if (file == null) {
            throw new AppException(400, "A photo file is required, as the multipart part \"file\"");
        }
        MediaType type = file.getMediaType();
        String contentType = type == null ? null : type.getType() + "/" + type.getSubtype();

        InputStream content = file.getContent();
        try {
            return UserDTO.of(users.setAvatar(caller().userId(), content, contentType));
        } finally {
            try {
                content.close();
            } catch (IOException ignored) {
                // the photo is stored or refused by now; a failed close changes neither
            }
        }
    }

    /**
     * {@code DELETE /api/users/me/avatar} — auth. Removes the profile photo and its file;
     * removing one that is not there is not an error.
     *
     * @return the account, with a null {@code avatarUrl}
     */
    @DELETE
    @Path("me/avatar")
    @Secured
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO removeAvatar() {
        return UserDTO.of(users.removeAvatar(caller().userId()));
    }

    /**
     * @return the caller, which {@code @Secured} has already guaranteed exists
     */
    private SessionUser caller() {
        return currentUser.from(request).orElseThrow(() -> new IllegalStateException(
                "no caller on a @Secured endpoint; the annotation is missing or the filter is not bound"));
    }
}
