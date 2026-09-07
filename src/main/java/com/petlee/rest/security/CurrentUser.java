package com.petlee.rest.security;

import com.petlee.session.SessionLifecycle;

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
     * Logs a user in: gives the caller a session identifier it has never seen before, and stores
     * the user in it.
     *
     * <h2>Why the identifier changes</h2>
     * Session fixation. An attacker who can set a victim's {@code JSESSIONID} — through a link, a
     * subdomain cookie, an XSS foothold — waits for them to log in and then uses the same
     * identifier, now authenticated. Changing it at login makes the pre-login identifier
     * worthless, because the value the browser holds afterwards is one the attacker never saw.
     * This is why T-20 must not simply call {@code setAttribute} on the session it already has,
     * and why that decision is made here rather than left to the caller.
     *
     * <h2>Why {@code changeSessionId} and not invalidate-then-recreate</h2>
     * Both make the old identifier worthless, and the first version of this method did the
     * second. It cannot be used here, because of ADR-001: the Faces tier reaches this endpoint
     * over loopback HTTP, and the two requests share one {@code HttpSession}. Destroying it from
     * the loopback request leaves the <em>browser's</em> request standing on an object the
     * container has already torn down, and the next line of Faces code to touch a
     * {@code @SessionScoped} bean fails with
     * {@code IllegalStateException: getAttribute: Session already invalidated}. That is not a
     * theoretical risk — it is what T-26's {@code UserManagedBean} does one statement after
     * {@code ApiClient.login} returns, and it was reproduced before this method was changed.
     *
     * <p>{@link HttpServletRequest#changeSessionId()} is the Servlet API written for exactly this:
     * the identifier is replaced and the container sends the new one to the client, while the
     * session object itself stays alive, so nothing holding a reference to it is harmed. The
     * attributes carry over, which is the accepted trade — an attacker who fixates an identifier
     * knows that string and nothing else; they never had a way to read or write the session's
     * server-side attributes. OWASP recommends this call for Servlet containers for the same
     * reason.
     *
     * <p>This is the only {@code getSession(true)} in the project. A grep for it should find one
     * hit, in this method, on the path where the caller arrived with no session at all — there
     * being no identifier to change, and the one about to be created never having been exposed.
     *
     * @param request the current request; must not be {@code null}
     * @param user    the authenticated user; must not be {@code null}
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
     * <h2>Unless the caller is destroying it itself</h2>
     * Under ADR-001 the Faces tier reaches this endpoint over loopback HTTP and the two requests
     * share one session. Invalidating it here leaves the browser's request holding a torn-down
     * object, and the next {@code @SessionScoped} bean it touches fails with
     * {@code IllegalStateException: getAttribute: Session already invalidated} — which is exactly
     * what T-26's {@code UserManagedBean.logout()} does one statement later.
     *
     * <p>So a caller in the same JVM may take responsibility for the destruction, declaring it
     * through {@link SessionLifecycle#deferDiscardToCaller}; this method then clears its own state
     * and leaves the session for the caller to end on the browser's own thread. The session dies
     * either way. The flag is a server-side attribute, so no external client can set it and no
     * external client takes this path.
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
            if (SessionLifecycle.discardIsDeferred(session)) {
                session.removeAttribute(SESSION_ATTRIBUTE);
            } else {
                session.invalidate();
            }
        } catch (IllegalStateException alreadyGone) {
            // Two logouts racing. Both callers get what they asked for.
        }
    }
}
