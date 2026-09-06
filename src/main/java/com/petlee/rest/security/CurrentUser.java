package com.petlee.rest.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The only code in the project that reads or writes the session's user attribute.
 *
 * <p>Four operations, and every session-touching line of the REST tier goes through one of them:
 * {@link #establish} at login, {@link #from} and {@link #userIdOrNull} on each request,
 * {@link #terminate} at logout. Concentrating them here is what makes two rules checkable rather
 * than hoped for — that {@code getSession(true)} appears in exactly one place, and that the
 * attribute name {@code petlee.user} is spelled once.
 *
 * <h2>Reading never creates a session</h2>
 * {@link #from} and {@link #userIdOrNull} call {@code getSession(false)}. The {@code true}
 * variant would create a session for every anonymous request — a {@code Set-Cookie} on a plain
 * {@code GET /api/pets}, a session object per crawler hit, and an authentication filter that can
 * no longer distinguish "has a session" from "was here once", which is the check it exists to
 * perform.
 *
 * <h2>Static by design</h2>
 * There is no state to hold and nothing to inject: everything comes from the request that is
 * passed in. A CDI bean would additionally have to be injectable into providers, resources and
 * (through T-24) the web tier, all to wrap two calls on an argument the caller already has.
 */
public final class CurrentUser {

    private static final Logger LOGGER = Logger.getLogger(CurrentUser.class.getName());

    /**
     * The session attribute holding the {@link SessionUser}.
     *
     * <p>Prefixed with the application name because the session is shared with the Faces tier
     * (ADR-001: one WAR, one session manager), and an unprefixed name like {@code "user"} is one
     * a JSF page could plausibly reuse for something else.
     */
    public static final String SESSION_ATTRIBUTE = "petlee.user";

    private CurrentUser() {
    }

    /**
     * Reads the logged-in user without creating a session.
     *
     * @param request the current request; {@code null} is tolerated and reported as absent, so a
     *                caller need not guard before asking
     * @return the session's user, or empty when there is no session, the session has expired, or
     *         it holds no user
     */
    public static Optional<SessionUser> from(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        try {
            Object attribute = session.getAttribute(SESSION_ATTRIBUTE);
            // instanceof rather than a cast: the session is shared with the Faces tier, and a
            // ClassCastException here would surface as a 500 on an unrelated request.
            return attribute instanceof SessionUser user ? Optional.of(user) : Optional.empty();
        } catch (IllegalStateException expired) {
            // The session was invalidated between getSession(false) and getAttribute - a logout
            // racing this request, or expiry. Not being logged in is the right answer, and it is
            // the answer that produces a 401 rather than a 500 (T-18 criterion 6).
            return Optional.empty();
        }
    }

    /**
     * The caller's id, for endpoints that are open but behave differently for a logged-in user —
     * {@code GET /api/pets/{id}}, where {@code api-contract.md} says owner contact fields are
     * filled <em>"ONLY if the caller is logged in; otherwise null"</em>.
     *
     * <p>Returns {@code null} rather than an {@code Optional} because that is what the service
     * layer's viewer parameters take: a {@code null} viewer means a guest, and the privacy rule is
     * already written against that convention.
     *
     * @param request the current request, possibly {@code null}
     * @return the logged-in user's id, or {@code null} for a guest
     */
    public static Long userIdOrNull(HttpServletRequest request) {
        return from(request).map(SessionUser::getUserId).orElse(null);
    }

    /**
     * Logs a user in: invalidates whatever session the caller arrived with, creates a new one, and
     * stores the user in it.
     *
     * <h2>Why the invalidate comes first</h2>
     * Session fixation. An attacker who can set a victim's {@code JSESSIONID} — through a link, a
     * subdomain cookie, an XSS foothold — waits for them to log in and then uses the same
     * identifier, now authenticated. Recreating the session at login makes the pre-login
     * identifier worthless, because the value the browser holds afterwards is one the attacker
     * never saw. This is why T-20 must not simply call {@code setAttribute} on the session it
     * already has, and why that decision is made here rather than left to the caller.
     *
     * <p>This is the only {@code getSession(true)} in the project. A grep for it should find one
     * hit, in this method.
     *
     * @param request the current request; must not be {@code null}
     * @param user    the authenticated user; must not be {@code null}
     */
    public static void establish(HttpServletRequest request, SessionUser user) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(user, "user");

        HttpSession existing = request.getSession(false);
        if (existing != null) {
            try {
                existing.invalidate();
            } catch (IllegalStateException alreadyGone) {
                // Already invalid, which is the state this call was trying to reach.
            }
        }

        HttpSession fresh = request.getSession(true);
        fresh.setAttribute(SESSION_ATTRIBUTE, user);

        LOGGER.log(Level.FINE, () -> "Session established for " + user.getUsername());
    }

    /**
     * Logs a user out by invalidating the session — the counterpart of {@link #establish}, and the
     * reason {@link #SESSION_ATTRIBUTE} is never spelled in T-20.
     *
     * <p>Removing the attribute would not be enough: the session, its identifier and anything the
     * Faces tier put in it would survive, and the browser would keep sending a cookie that is
     * still valid for whatever else reads that session.
     *
     * @param request the current request, possibly {@code null}
     */
    public static void terminate(HttpServletRequest request) {
        if (request == null) {
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            // Logging out without a session is not an error: api-contract.md gives logout a 204,
            // and a guest calling it has already achieved what it asks for.
            return;
        }

        try {
            session.invalidate();
        } catch (IllegalStateException alreadyGone) {
            // Two logouts racing. Both callers get what they asked for.
        }
    }
}
