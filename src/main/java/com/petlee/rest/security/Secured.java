package com.petlee.rest.security;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a resource class or method that requires a logged-in user.
 *
 * <h2>The rule, stated plainly</h2>
 * <strong>Every {@code @POST}, {@code @PUT} and {@code @DELETE} resource method in this project
 * carries {@code @Secured} or {@link AdminOnly}. There are no exceptions.</strong> That is
 * specification §4's endpoint-security requirement — <em>"Web services will enforce session-based
 * user authentication on every HTTP request that modifies data"</em> — expressed as something a
 * reviewer can check by reading one line above each method rather than by reasoning about the
 * method's body.
 *
 * <p>A method that is missing it is not protected by anything else. There is no servlet filter
 * behind this one and no security constraint in {@code web.xml}; the presence of the annotation
 * <em>is</em> the protection, because {@link AuthenticationFilter} is bound to it by name and runs
 * for nothing else.
 *
 * <h2>What it does not mark</h2>
 * Endpoints that are open but session-sensitive — {@code GET /api/pets/{id}}, marked {@code open*}
 * in {@code api-contract.md} — must <em>not</em> carry it. A guest is allowed through there; only
 * the content changes, and {@link CurrentUser#userIdOrNull(jakarta.servlet.http.HttpServletRequest)}
 * is how the resource decides what to include.
 *
 * <h2>Placement</h2>
 * On a class it applies to every method of that class. On a method it applies to that method
 * alone. Where most of a resource is protected and one method is open, annotate the methods, not
 * the class: a future open method added to a {@code @Secured} class inherits protection nobody
 * asked for, which is the failure that is noticed, whereas the reverse is the failure that is not.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Secured {
}
