package com.petlee.rest.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decides who is calling the API, from either of the two credentials it accepts: an
 * {@code Authorization: Bearer} token issued by {@link TokenStore}, or the session cookie.
 * The only code that reads or writes the session's user attribute. Reading never creates a
 * session, and {@code getSession(true)} appears once in the project — in {@link #establish}.
 *
 * <p>A CDI bean rather than static helpers because token lookup needs the injected
 * {@link TokenStore}; every caller injects this one instead of reaching for either store itself.
 */
@ApplicationScoped
public class CurrentUser {

    private static final Logger LOGGER = Logger.getLogger(CurrentUser.class.getName());

    /** The session attribute holding the {@link SessionUser}, prefixed because the Faces tier
     * shares this session. */
    public static final String SESSION_ATTRIBUTE = "petlee.user";

    private static final String BEARER_PREFIX = "Bearer ";

    private TokenStore tokens;

    /** For CDI only. */
    protected CurrentUser() {
    }

    @Inject
    public CurrentUser(TokenStore tokens) {
        this.tokens = tokens;
    }

    /**
     * Reads the caller without creating a session. A bearer header, when present, decides alone:
     * an unknown or expired token is "not logged in" even if a valid session cookie came along.
     *
     * @param request the current request; null is tolerated and reported as absent
     * @return the caller, or empty when neither credential identifies anybody
     */
    public Optional<SessionUser> from(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        Optional<String> token = bearerToken(request);
        if (token.isPresent()) {
            return tokens.resolve(token.get());
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
     * The token from an {@code Authorization: Bearer <token>} header. The scheme is matched
     * case-insensitively, as RFC 7235 requires; any other scheme is ignored.
     *
     * @param request the current request, possibly null
     * @return the token — possibly empty text for a bare {@code "Bearer "} — or empty when the
     *         request carries no bearer header
     */
    public Optional<String> bearerToken(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String header = request.getHeader("Authorization");
        if (header == null
                || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()).trim());
    }

    /**
     * Logs a user in, rotating the session identifier so a fixated pre-login one is worthless.
     * {@code changeSessionId} rather than invalidate-and-recreate, which would tear the session
     * out from under the {@code @SessionScoped} beans about to be touched.
     *
     * @param request the current request; must not be null
     * @param user    the authenticated user; must not be null
     */
    public void establish(HttpServletRequest request, SessionUser user) {
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
    public void terminate(HttpServletRequest request) {
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
