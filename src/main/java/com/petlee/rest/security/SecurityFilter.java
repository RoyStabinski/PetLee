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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Turns {@link Secured} and {@link AdminOnly} into 401s and 403s, once, for every endpoint that
 * carries either.
 *
 * <h2>Deliberately not name-bound</h2>
 * This class carries neither {@code @Secured} nor {@code @AdminOnly} itself. It is a plain
 * {@code @Provider}, which the Jakarta REST runtime therefore invokes for <em>every</em> request,
 * and {@link #matched(Class)} decides — by reading {@link ResourceInfo} with ordinary reflection —
 * whether the matched method or class carries either annotation. An open endpoint (no annotation
 * of either kind) is let through untouched in {@link #filter}.
 *
 * <p>The tempting alternative is to put {@code @Secured} and {@code @AdminOnly} on this class and
 * let Jakarta REST's own name-binding machinery decide. Do not: name binding is a logical AND —
 * <strong>a provider annotated with two binding annotations is invoked only for a resource that
 * carries both of them</strong> (Jakarta RESTful Web Services specification, "Name Binding"). No
 * resource in this project carries both {@code @Secured} and {@code @AdminOnly}
 * ({@link AdminResource} and the admin methods of {@link CategoryResource} carry
 * {@code @AdminOnly} alone; everything else that is protected carries {@code @Secured} alone), so
 * a filter bound to both would never fire for any of them — every endpoint, including the ones
 * that only need {@code @Secured}, would be silently unguarded. This is not a theoretical reading:
 * it is the same failure mode a predecessor of this class hit in production, the first time these
 * two checks lived in one class carrying both bindings — a logout call with no session answered
 * 204 instead of 401, because the filter it relied on was never invoked. Reading the target
 * method's own annotations with reflection, as {@link #matched} does, has no such trap: it does
 * not depend on how any particular Jakarta REST implementation resolves binding, so nothing here
 * needs verifying against a specific server.
 *
 * <h2>Why {@code @AdminOnly} alone is enough on a resource</h2>
 * {@link #filter} treats "the method or class carries {@code @AdminOnly}" as also requiring
 * authentication — see {@link #adminRequired()} folded into the same branch that decides whether a
 * session is required at all. A resource author never has to remember to add {@code @Secured}
 * alongside {@code @AdminOnly}: forgetting it cannot happen, because there is nothing to forget.
 *
 * <h2>401 before 403</h2>
 * A guest hitting an {@code @AdminOnly} endpoint is told "you must log in", not "you are not an
 * administrator" — {@link #filter} checks authentication first and only asks about the admin flag
 * once a session is confirmed. That ordering is what this class controls; it says nothing about
 * whether an endpoint's existence is otherwise discoverable (an {@code OPTIONS} request, for
 * instance, is answered by the container's own routing before this filter ever runs).
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
 * session cookie. The body is an {@link ErrorDTO} instead.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class SecurityFilter implements ContainerRequestFilter {

    /** The Servlet request, which is where the session lives. */
    @Context
    private HttpServletRequest request;

    /** Which method matched, so {@link #matched(Class)} can read its annotations. */
    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext context) {
        boolean adminRequired = matched(AdminOnly.class);
        if (!matched(Secured.class) && !adminRequired) {
            return; // open endpoint: neither annotation present, nothing to enforce
        }

        Optional<SessionUser> caller = CurrentUser.from(request);
        if (caller.isEmpty()) {
            // Covers all three ways of arriving without a session: never had one, the cookie
            // names a session that has expired, and a session that holds no user. All three are
            // "not logged in", and none of them is a server error.
            abort(context, 401, "NOT_AUTHENTICATED", "You must be logged in to do that.");
            return;
        }

        if (adminRequired && !caller.get().admin()) {
            abort(context, 403, "NOT_ADMIN", "Administrator privileges are required.");
        }
    }

    /**
     * Whether the matched method — or its class — carries {@code annotationType}.
     *
     * <p>{@code getResourceMethod()}/{@code getResourceClass()} are null for a request that never
     * matched a resource (a 404), but this filter runs at {@code Priorities.AUTHENTICATION} for
     * every request regardless, so both are checked for null before use. Live-confirmed on Payara
     * that post-matching filters are never invoked for an unmatched path, so this is not currently
     * exploitable — but the most security-critical method in this application should not rest on
     * an assumption about a particular server's internals.
     *
     * @param annotationType {@link Secured} or {@link AdminOnly}
     * @return whether the annotation is present on the method or the resource class
     */
    private boolean matched(Class<? extends Annotation> annotationType) {
        Method method = resourceInfo.getResourceMethod();
        Class<?> type = resourceInfo.getResourceClass();
        return (method != null && method.isAnnotationPresent(annotationType))
                || (type != null && type.isAnnotationPresent(annotationType));
    }

    private static void abort(ContainerRequestContext context, int status, String code,
                              String message) {
        context.abortWith(Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO(code, message))
                .build());
    }
}
