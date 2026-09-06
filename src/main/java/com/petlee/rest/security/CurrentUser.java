package com.petlee.rest.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The only code that reads or writes the session's user attribute. Reading never creates a
 * session, and {@code getSession(true)} appears once in the project — in {@link #establish}.
 */
public final class CurrentUser {

    private static final Logger LOGGER = Logger.getLogger(CurrentUser.class.getName());

    /** The session attribute holding the {@link SessionUser}, prefixed because the Faces tier
     * shares this session. */
    public static final String SESSION_ATTRIBUTE = "petlee.user";

    private CurrentUser() {
    }

    /**
     * Reads the logged-in user without creating a session.
     *
     * @param request the current request; null is tolerated and reported as absent
     * @return the session's user, or empty when there is no session, it expired, or it holds none
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
            // instanceof rather than a cast: the Faces tier shares this session.
            return attribute instanceof SessionUser user ? Optional.of(user) : Optional.empty();
        } catch (IllegalStateException expired) {
            // Invalidated underneath us by a racing logout. "Not logged in" is the right answer.
            return Optional.empty();
        }
    }

    /**
     * Logs a user in, rotating the session identifier so a fixated pre-login one is worthless.
     * {@code changeSessionId} rather than invalidate-and-recreate, which would tear the session
     * out from under the {@code @SessionScoped} beans about to be touched.
     *
     * @param request the current request; must not be null
     * @param user    the authenticated user; must not be null
     */
    public static void establish(HttpServletRequest request, SessionUser user) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(user, "user");

        HttpSession session = request.getSession(false);
        if (session == null) {
            session = request.getSession(true);
        } else {
            request.changeSessionId();
        }

        session.setAttribute(SESSION_ATTRIBUTE, user);

        LOGGER.log(Level.FINE, () -> "Session established for " + user.username());
    }

    /**
     * Logs a user out by invalidating the session, not merely clearing the attribute, so the
     * cookie the browser holds stops being valid.
     *
     * @param request the current request, possibly null
     */
    public static void terminate(HttpServletRequest request) {
        if (request == null) return;
        HttpSession session = request.getSession(false);
        if (session == null) return;
        try {
            session.invalidate();
        } catch (IllegalStateException alreadyGone) {
            // already invalidated by another request; nothing to do
        }
    }
}
