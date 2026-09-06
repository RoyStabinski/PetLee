package com.petlee.rest.security;

import com.petlee.dto.ErrorDTO;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns {@link Secured} and {@link AdminOnly} into 401s and 403s, once, for every endpoint that
 * carries them.
 *
 * <h2>Name binding: it runs for annotated endpoints only</h2>
 * The {@link Secured} annotation on this class is what binds it. A {@code @Provider} filter with
 * no name binding runs on <em>every</em> request, which would 401 the open endpoints the contract
 * requires to work for guests — {@code GET /api/pets}, {@code GET /api/categories},
 * {@code /api/health}.
 *
 * <p>It is bound to {@code @Secured} <em>only</em>, and {@link AdminOnlyFilter} is a separate
 * provider bound to {@code @AdminOnly}, because name bindings intersect rather than union: a
 * provider carrying two of them applies to a method carrying <em>both</em>. One filter annotated
 * with both therefore protects nothing that is only {@code @Secured} — which is not a theoretical
 * reading, it is what a deployed server did with the first version of this class, answering 204 to
 * a logout with no session. The shared decision below is what keeps the two filters from drifting.
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

    @Override
    public void filter(ContainerRequestContext context) {
        apply(context, request, false);
    }

    /**
     * The check both filters run: refuse the request, or let it through.
     *
     * @param context       the request being filtered, aborted on refusal
     * @param request       the Servlet request carrying the session
     * @param adminRequired whether the endpoint is {@link AdminOnly}
     */
    static void apply(ContainerRequestContext context, HttpServletRequest request,
                      boolean adminRequired) {
        Rejection rejection = decide(request, adminRequired);
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
     * @param request       the Servlet request carrying the session
     * @param adminRequired whether the endpoint is {@link AdminOnly}
     * @return the rejection to send, or {@code null} when the request may proceed
     */
    static Rejection decide(HttpServletRequest request, boolean adminRequired) {
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
        if (adminRequired && !user.isAdmin()) {
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
     * The method and path, for the log line. Never the session id, never the cookie header: a log
     * line holding a session identifier is a credential written to a file with different
     * permissions from the session store, and anything that reads it can impersonate the user.
     */
    private static String describe(ContainerRequestContext context) {
        return context.getMethod() + " " + context.getUriInfo().getPath();
    }
}
