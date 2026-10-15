package com.petlee.rest;

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
import jakarta.ws.rs.core.UriInfo;

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

    /** For the Location header of a 201 Created. */
    @Context
    private UriInfo uriInfo;

    /** For the container. Public, as Jakarta REST requires of a root resource class. */
    public UserResource() {
    }

    /**
     * @param users       the account rules
     * @param currentUser resolves the caller from the bearer token or the session
     * @param tokens      the bearer tokens, for revoking them on a password change
     */
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
     * <p>There is no endpoint that reads a user by id, so the Location header names
     * {@code /api/users/me}, where the new member reads the account once logged in.
     *
     * @param form the registration body
     * @return 201 Created, with {@code Location: /api/users/me} and the created user as the body
     */
    @POST
    @Path("register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(RegisterForm form) {
        if (form == null) {
            throw new AppException(400, "A request body is required");
        }
        User user = users.register(form);
        return Response.created(uriInfo.getBaseUriBuilder().path(UserResource.class).path("me").build())
                .entity(UserDTO.of(user)).build();
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
     * @return the caller, which {@code @Secured} has already guaranteed exists
     */
    private SessionUser caller() {
        return currentUser.from(request).orElseThrow(() -> new IllegalStateException(
                "no caller on a @Secured endpoint; the annotation is missing or the filter is not bound"));
    }
}
