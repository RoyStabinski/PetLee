package com.petlee.rest;

import com.petlee.dto.LoginForm;
import com.petlee.dto.UserDTO;
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
 * {@code /api/auth} — login and logout, the two endpoints that own the session.
 *
 * <h2>Where sessions may be touched</h2>
 * Here and in T-18's {@link CurrentUser}, nowhere else. No service reads a session — that is the
 * standing rule that keeps authorisation testable, with the caller's id passed in as an argument —
 * and no other resource creates or destroys one. Both methods below go through {@code CurrentUser}
 * rather than calling {@code HttpSession} directly, so the fixation defence cannot be forgotten in
 * one place and remembered in another.
 *
 * <h2>Passwords</h2>
 * The login body carries one in plaintext. It is not logged, not echoed, and not stored in the
 * session: {@link SessionUser} has nowhere to put it.
 */
@Path("auth")
@RequestScoped
public class AuthResource {

    private UserService users;

    /**
     * The Servlet request, for the session. Jakarta REST has no session of its own, and the
     * {@code HttpSession} reached through this is the same one the Faces tier uses — one WAR, one
     * session manager (ADR-001), which is what lets T-24 forward the browser's cookie.
     */
    @Context
    private HttpServletRequest request;

    /**
     * For the container. It is {@code public}, not {@code protected}: CDI only needs something it
     * can proxy, but the Jakarta REST specification requires a root resource class to have a
     * public constructor, and RESTEasy enforces it — a protected one deploys on Payara and fails
     * on WildFly with "could not find constructor for class".
     */
    public AuthResource() {
    }

    @Inject
    public AuthResource(UserService users) {
        this.users = users;
    }

    /**
     * {@code POST /api/auth/login} — open.
     *
     * <p>The contract: <em>"Response 200: same UserDTO as above, plus a session is created"</em>,
     * and <em>"Errors: 401 if credentials are wrong"</em>.
     *
     * <p>The 401 comes from {@link UserService#authenticate} through T-19, and it is deliberately
     * identical for an unknown username and a wrong password. The session is established only
     * after that call returns, so a failed attempt leaves the caller with exactly what they had —
     * criterion 4 asserts there is no {@code Set-Cookie} on a rejection.
     *
     * <p>A missing body is treated as empty credentials rather than as a server error: no username
     * is no more valid than a wrong one, and both are the same 401.
     *
     * @param form the credentials
     * @return the authenticated user
     */
    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO login(LoginForm form) {
        LoginForm credentials = form != null ? form : new LoginForm();

        UserDTO user = users.authenticate(credentials.getUsername(), credentials.getPassword());

        // Rotates the session id as it goes - see CurrentUser.establish on why that is not
        // optional, and why the decision does not live in this method.
        CurrentUser.establish(request, SessionUser.of(user));

        return user;
    }

    /**
     * {@code POST /api/auth/logout} — auth.
     *
     * <p>The contract: <em>"Request: (empty)"</em>, <em>"Response 204: no body. Session
     * invalidated."</em> A body here — even {@code {"status":"ok"}} — would be a deviation, so the
     * method returns {@code noContent()} rather than a DTO.
     *
     * <p>{@code @Secured} means logging out without a session is a 401 rather than a cheerful 204.
     * That is the contract's own rule for an endpoint marked <em>auth</em>, and it also keeps the
     * endpoint from being a way to probe whether an arbitrary session id is live.
     *
     * <p>The session is invalidated, not emptied: the identifier the browser holds stops being
     * valid, which is what makes it unusable on the next request (criterion 7).
     *
     * @return {@code 204 No Content}
     */
    @POST
    @Path("logout")
    @Secured
    public Response logout() {
        CurrentUser.terminate(request);
        return Response.noContent().build();
    }
}
