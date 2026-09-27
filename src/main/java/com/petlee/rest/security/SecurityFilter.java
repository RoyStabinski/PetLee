package com.petlee.rest.security;

import com.petlee.dto.ErrorDTO;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
 * Turns {@link Secured} and {@link AdminOnly} into 401s and 403s for every endpoint carrying
 * either, and treats {@code @AdminOnly} as implying authentication so it can stand alone.
 *
 * <p>Deliberately not name-bound: Jakarta REST name binding is an AND, so a filter annotated with
 * both would fire only for a resource carrying both — that is, for none of them. This runs for
 * every request instead and reads the matched method's annotations by reflection.
 *
 * <p>{@code @ApplicationScoped} so CDI injects {@link CurrentUser}: with
 * {@code bean-discovery-mode="annotated"} a bare {@code @Provider} is not a bean.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class SecurityFilter implements ContainerRequestFilter {

    /** Resolves the caller from a bearer token or the session. */
    @Inject
    private CurrentUser currentUser;

    /** The Servlet request, which carries the Authorization header and the session. */
    @Context
    private HttpServletRequest request;

    /** Which method matched, so {@link #matched(Class)} can read its annotations. */
    @Context
    private ResourceInfo resourceInfo;

    /** Rejects the request when the matched endpoint requires a caller, or an admin, and the
     * caller is neither. An unknown or expired bearer token counts as no caller. Authentication
     * is checked first, so a guest gets 401 rather than 403. */
    @Override
    public void filter(ContainerRequestContext context) {
        boolean adminRequired = matched(AdminOnly.class);
        if (!matched(Secured.class) && !adminRequired) {
            return; // open endpoint: neither annotation present, nothing to enforce
        }

        Optional<SessionUser> caller = currentUser.from(request);
        if (caller.isEmpty()) {
            abort(context, 401, "NOT_AUTHENTICATED", "You must be logged in to do that.");
            return;
        }

        if (adminRequired && !caller.get().admin()) {
            abort(context, 403, "NOT_ADMIN", "Administrator privileges are required.");
        }
    }

    /**
     * Whether the matched method, or its class, carries the annotation. Both are null-checked:
     * a request that matched no resource still reaches this filter.
     *
     * @param annotationType {@link Secured} or {@link AdminOnly}
     * @return whether the annotation is present
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
