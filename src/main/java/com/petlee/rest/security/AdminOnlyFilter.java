package com.petlee.rest.security;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

/**
 * The {@link AdminOnly} half of the pair — a logged-in caller whose role is {@code ADMIN}.
 *
 * <h2>Why this is a second class rather than a second annotation on the first</h2>
 * Jakarta REST name bindings intersect. A provider carrying both {@code @Secured} and
 * {@code @AdminOnly} is applied only to a resource method carrying <em>both</em>, so a single
 * filter annotated with the two would silently stop protecting every endpoint that is merely
 * {@code @Secured}. That is not a reading of the specification — it is what a deployed server did:
 * {@code POST /api/auth/logout} with no session answered 204 instead of 401, because the filter it
 * relied on was never invoked.
 *
 * <p>Splitting the binding across two providers is the fix, and the check itself stays in one
 * place: both delegate to {@link AuthenticationFilter#apply}, this one asking for the role test as
 * well. So {@code @AdminOnly} still implies {@code @Secured} — a guest reaching an admin endpoint
 * is refused with 401 before the role is ever considered.
 *
 * <h2>Priority</h2>
 * {@code AUTHORIZATION} rather than {@code AUTHENTICATION}: where a method carries both
 * annotations, both filters run, and the authentication one should go first. The outcome is the
 * same either way — the check is identical and repeating it is harmless — but the log line that
 * arrives first should be the one about identity.
 */
@Provider
@AdminOnly
@Priority(Priorities.AUTHORIZATION)
public class AdminOnlyFilter implements ContainerRequestFilter {

    @Context
    private HttpServletRequest request;

    @Override
    public void filter(ContainerRequestContext context) {
        AuthenticationFilter.apply(context, request, true);
    }
}
