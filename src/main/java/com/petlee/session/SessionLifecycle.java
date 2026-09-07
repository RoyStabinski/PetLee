package com.petlee.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Who is allowed to destroy the shared {@code HttpSession}, and when.
 *
 * <h2>Why this class exists</h2>
 * ADR-001 puts the Faces tier and the REST tier in one WAR so that they share one session manager
 * — that sharing is what lets a managed bean's loopback call be an authenticated call. It has a
 * consequence nobody planned for: <strong>two live requests hold the same {@code HttpSession}
 * object</strong>, the browser's and the loopback one, and whichever destroys it leaves the other
 * standing on a torn-down object.
 *
 * <p>That is not hypothetical. Before this class existed, {@code POST /api/auth/logout} invalidated
 * the session from the loopback request, and the next line of Faces code to touch a
 * {@code @SessionScoped} bean died with
 * {@code IllegalStateException: getAttribute: Session already invalidated} — which is precisely
 * what T-26's {@code UserManagedBean.logout()} does one statement later. Login had the same defect
 * and is fixed differently, by {@code CurrentUser.establish} changing the identifier instead of
 * replacing the session; logout has no such option, because ending the session is the entire point.
 *
 * <h2>The protocol</h2>
 * The browser-facing tier declares, before it calls, that it will do the destroying itself. The
 * REST tier then clears its own state and leaves the session alone. One statement later the
 * browser-facing tier destroys it — <em>on the request the browser is actually waiting on</em>, so
 * the container's session listeners fire on that thread and everything bound to the session,
 * CDI's session context included, is torn down in an order it can cope with.
 *
 * <p>The session is destroyed either way. What changes is which thread does it.
 *
 * <h2>Why the flag cannot be forged</h2>
 * It is a <strong>server-side session attribute</strong>, not a header or a parameter. Nothing a
 * client sends can set it, so an external caller — {@code curl}, a mobile client, an attacker —
 * always takes the ordinary path where the REST tier invalidates the session itself. The defence
 * is unchanged for everyone except the tier running in the same JVM.
 *
 * <h2>Why it lives in its own package</h2>
 * Both tiers need it and neither should depend on the other: {@code com.petlee.web} importing
 * {@code com.petlee.rest} would invert the layering as surely as the service import ADR-001 bans.
 * This package holds the session plumbing they genuinely share and nothing else.
 */
public final class SessionLifecycle {

    private static final Logger LOGGER = Logger.getLogger(SessionLifecycle.class.getName());

    /**
     * The flag. Package-visible naming convention matches {@code CurrentUser.SESSION_ATTRIBUTE} —
     * a dotted key that cannot collide with anything Faces or CDI stores.
     */
    static final String DEFERRED_ATTRIBUTE = "petlee.session.discardDeferred";

    private SessionLifecycle() {
    }

    /**
     * Declares that this request will destroy its own session, so a service called from it must
     * not. Does nothing when there is no session, which is the state the caller wanted anyway.
     *
     * @param request the browser-facing request, possibly {@code null}
     */
    public static void deferDiscardToCaller(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        if (session == null) {
            return;
        }
        try {
            session.setAttribute(DEFERRED_ATTRIBUTE, Boolean.TRUE);
        } catch (IllegalStateException alreadyGone) {
            // Nothing left to defer.
        }
    }

    /**
     * @param session the session about to be ended, possibly {@code null}
     * @return {@code true} when the caller has taken responsibility for destroying it
     */
    public static boolean discardIsDeferred(HttpSession session) {
        if (session == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(session.getAttribute(DEFERRED_ATTRIBUTE));
        } catch (IllegalStateException alreadyGone) {
            // Already destroyed: nobody needs to destroy it again.
            return true;
        }
    }

    /**
     * Destroys this request's session, from this request.
     *
     * <p>Idempotent and quiet: a session that has already gone, or was never there, is the state
     * this method exists to reach.
     *
     * @param request the browser-facing request, possibly {@code null}
     */
    public static void discard(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        if (session == null) {
            return;
        }
        try {
            session.invalidate();
            LOGGER.log(Level.FINE, "session discarded by the request that owns it");
        } catch (IllegalStateException alreadyGone) {
            // Two logouts racing, or the session expired underneath us. Both callers got what
            // they asked for.
        }
    }
}
