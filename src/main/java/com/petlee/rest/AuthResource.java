package com.petlee.rest;

import com.petlee.dto.LoginForm;
import com.petlee.dto.UserDTO;
import com.petlee.model.User;
import com.petlee.rest.security.CurrentUser;
import com.petlee.rest.security.Secured;
import com.petlee.rest.security.SessionUser;
import com.petlee.service.UserService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code /api/auth} — the only endpoints that create or destroy a session, and they do it
 * through {@link CurrentUser} so the fixation defence cannot be forgotten in one of them.
 */
@Path("auth")
@RequestScoped
public class AuthResource {

    private UserService users;

    /** For the session. It is the same HttpSession the Faces tier uses — one WAR, one manager. */
    @Context
    private HttpServletRequest request;

    /** For the container. Public, as Jakarta REST requires of a root resource class. */
    public AuthResource() {
    }

    @Inject
    public AuthResource(UserService users) {
        this.users = users;
    }

    /**
     * {@code POST /api/auth/login} — open. The session is established only once authentication
     * has returned, so a rejected attempt sets no cookie.
     *
     * @param form the credentials; a missing body is treated as empty ones
     * @return the authenticated user
     */
    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO login(LoginForm form) {
        LoginForm credentials = form != null ? form : new LoginForm(null, null);

        User user = users.authenticate(credentials.username(), credentials.password());

        // Rotates the session id as it goes; see CurrentUser.establish.
        CurrentUser.establish(request, SessionUser.of(user));

        return UserDTO.of(user);
    }

    /**
     * {@code POST /api/auth/logout} — auth. Invalidates the session rather than emptying it.
     *
     * @return 204 No Content
     */
    @POST
    @Path("logout")
    @Secured
    public Response logout() {
        CurrentUser.terminate(request);
        return Response.noContent().build();
    }
}
