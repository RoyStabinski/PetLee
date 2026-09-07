package com.petlee.rest.security;

import com.petlee.dto.ErrorDTO;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns {@link Secured} and {@link AdminOnly} into 401s and 403s, once, for every endpoint that
 * carries them.
 *
 * <h2>Name binding: it runs for annotated endpoints only</h2>
 * The two annotations on this class are what bind it. A {@code @Provider} filter with no name
 * binding runs on <em>every</em> request, which would 401 the open endpoints the contract
 * requires to work for guests — {@code GET /api/pets}, {@code GET /api/categories},
 * {@code /api/health}. Binding to both annotations rather than only {@link Secured} is how
 * {@code @AdminOnly} comes to imply {@code @Secured} without the caller having to write both.
 *
 * <h2>Order</h2>
 * {@code Priorities.AUTHENTICATION} is the lowest-numbered standard priority, so this runs before
 * any other request filter. Nothing should observe a request that is about to be rejected.
 *
 * <h2>It does not touch the database</h2>
 * Everything it needs was written into the session at login. A per-request user lookup would add a
 * query to every protected call to detect a change this system does not make mid-session — see
 * {@link SessionUser}'s note on snapshots.
 *
 * <h2>What it deliberately does not send</h2>
 * No {@code WWW-Authenticate} header on the 401. It would make browsers open a native
 * username/password dialog for an application whose login is a form and whose credential is a
 * session cookie. The body is an {@link ErrorDTO} instead, which is what {@code api-contract.md}
 * promises and what T-24 can act on.
 */
@Provider
@Secured
@AdminOnly
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(AuthenticationFilter.class.getName());

    static final String NOT_AUTHENTICATED = "NOT_AUTHENTICATED";
    static final String NOT_ADMIN = "NOT_ADMIN";

    /**
     * The Servlet request, which is where the session lives. Jakarta REST has no session concept
     * of its own; {@code SecurityContext} describes container-managed authentication, which this
     * application does not use.
     */
    @Context
    private HttpServletRequest request;

    /**
     * The resource class and method this request matched. It is how the filter tells a
     * {@link Secured} endpoint from an {@link AdminOnly} one while being bound to both.
     */
    @Context
    private ResourceInfo resourceInfo;

    /** For the container, which instantiates providers with a no-argument constructor. */
    public AuthenticationFilter() {
    }

    /** For tests, which have no container to perform {@code @Context} injection. */
    AuthenticationFilter(HttpServletRequest request, ResourceInfo resourceInfo) {
        this.request = request;
        this.resourceInfo = resourceInfo;
    }

    @Override
    public void filter(ContainerRequestContext context) {
        Rejection rejection = decide();
        if (rejection == null) {
            return;
        }

        LOGGER.log(Level.FINE, () -> rejection.status.getStatusCode() + " " + describe(context)
                + ": " + rejection.reason);

        context.abortWith(Response.status(rejection.status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO(rejection.code, rejection.message))
                .build());
    }

    /**
     * The decision itself, separated from the response that carries it.
     *
     * <p>Split out because {@code Response.status(...)} needs a Jakarta REST runtime to build
     * anything, and ADR-003 keeps one out of the test classpath — the platform supplies it, and no
     * implementation may be added as a dependency. With the decision on this side of the line, who
     * is let through is covered by ordinary unit tests, and only the encoding waits for a deployed
     * server. The split is not merely a testing device either: what to decide and how to say it are
     * two different concerns, and T-19 draws the same line through the exception mappers.
     *
     * @return the rejection to send, or {@code null} when the request may proceed
     */
    Rejection decide() {
        Optional<SessionUser> caller = CurrentUser.from(request);

        if (caller.isEmpty()) {
            // Covers all three ways of arriving without a session: never had one, the cookie
            // names a session that has expired, and a session that holds no user. All three are
            // "not logged in", and none of them is a server error.
            return new Rejection(Response.Status.UNAUTHORIZED, NOT_AUTHENTICATED,
                    "You must be logged in to perform this action.",
                    "no authenticated session");
        }

        SessionUser user = caller.get();
        if (adminRequired() && !user.isAdmin()) {
            return new Rejection(Response.Status.FORBIDDEN, NOT_ADMIN,
                    "Administrator privileges are required for this action.",
                    user.getUsername() + " is not an administrator");
        }

        return null;
    }

    /**
     * A refusal: the status and {@link ErrorDTO} fields the client gets, plus the detail that goes
     * to the log and nowhere else.
     */
    static final class Rejection {

        final Response.Status status;
        final String code;
        final String message;

        /** Why, in words, for the server log. Never sent to the client. */
        final String reason;

        Rejection(Response.Status status, String code, String message, String reason) {
            this.status = status;
            this.code = code;
            this.message = message;
            this.reason = reason;
        }
    }

    /**
     * @return whether the matched endpoint carries {@link AdminOnly}, on the method or on its
     *         resource class. A missing {@code ResourceInfo} — which no container produces, but a
     *         mistake in a test would — is treated as not requiring admin, because the
     *         authentication check above has already run and the alternative would be to deny
     *         every request for a reason that has nothing to do with the caller.
     */
    private boolean adminRequired() {
        if (resourceInfo == null) {
            return false;
        }

        Method method = resourceInfo.getResourceMethod();
        if (method != null && method.isAnnotationPresent(AdminOnly.class)) {
            return true;
        }

        Class<?> resource = resourceInfo.getResourceClass();
        return resource != null && resource.isAnnotationPresent(AdminOnly.class);
    }

    private static void abort(ContainerRequestContext context, Response.Status status,
                              String code, String message) {
        context.abortWith(Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO(code, message))
                .build());
    }

    /**
     * The method and path, for the log line. Never the session id, never the cookie header: a log
     * line holding a session identifier is a credential written to a file with different
     * permissions from the session store, and anything that reads it can impersonate the user.
     */
    private static String describe(ContainerRequestContext context) {
        return context.getMethod() + " " + context.getUriInfo().getPath();
    }
}
