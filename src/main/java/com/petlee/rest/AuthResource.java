package com.petlee.rest;

import com.petlee.dto.LoginForm;
import com.petlee.dto.LoginResponse;
import com.petlee.dto.UserDTO;
import com.petlee.model.User;
import com.petlee.rest.security.CurrentUser;
import com.petlee.rest.security.Secured;
import com.petlee.rest.security.SessionUser;
import com.petlee.rest.security.TokenStore;
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
 * {@code /api/auth} — the only endpoints that create or destroy a session or a bearer token, and
 * they do it through {@link CurrentUser} and {@link TokenStore} so the fixation defence cannot be
 * forgotten in one of them.
 */
@Path("auth")
@RequestScoped
public class AuthResource {

    private UserService users;

    private CurrentUser currentUser;

    private TokenStore tokens;

    /** For the session. It is the same HttpSession the Faces tier uses — one WAR, one manager. */
    @Context
    private HttpServletRequest request;

    /** For the container. Public, as Jakarta REST requires of a root resource class. */
    public AuthResource() {
    }

    @Inject
    public AuthResource(UserService users, CurrentUser currentUser, TokenStore tokens) {
        this.users = users;
        this.currentUser = currentUser;
        this.tokens = tokens;
    }

    /**
     * {@code POST /api/auth/login} — open. The session is established, and the token issued, only
     * once authentication has returned, so a rejected attempt sets no cookie and gets no token.
     * Cookie clients can ignore the token; token clients can ignore the cookie.
     *
     * @param form the credentials; a missing body is treated as empty ones
     * @return the authenticated user and a new bearer token
     */
    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LoginResponse login(LoginForm form) {
        LoginForm credentials = form != null ? form : new LoginForm(null, null);

        User user = users.authenticate(credentials.username(), credentials.password());
        SessionUser caller = SessionUser.of(user);

        // Rotates the session id as it goes; see CurrentUser.establish.
        currentUser.establish(request, caller);

        return new LoginResponse(UserDTO.of(user), tokens.issue(caller));
    }

    /**
     * {@code POST /api/auth/logout} — auth. Revokes the bearer token if one was sent, and
     * invalidates the session rather than emptying it if there is one.
     *
     * @return 204 No Content
     */
    @POST
    @Path("logout")
    @Secured
    public Response logout() {
        currentUser.bearerToken(request).ifPresent(tokens::revoke);
        currentUser.terminate(request);
        return Response.noContent().build();
    }
}
