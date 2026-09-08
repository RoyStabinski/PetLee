package com.petlee.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The protocol that keeps the two tiers from tearing the shared session out from under each other.
 *
 * <p>The behaviour that matters is not "a flag can be set and read" but "nothing throws when the
 * session has already gone" — every method here is called on a session another request may have
 * ended a microsecond earlier, and an escaping {@code IllegalStateException} would turn a logout
 * into a 500.
 */
@DisplayName("SessionLifecycle")
class SessionLifecycleTest {

    @Test
    void deferringIsVisibleToWhoeverIsAboutToEndTheSession() {
        FakeRequest request = new FakeRequest(new FakeSession());

        assertFalse(SessionLifecycle.discardIsDeferred(request.session.asSession()));
        SessionLifecycle.deferDiscardToCaller(request.asServletRequest());
        assertTrue(SessionLifecycle.discardIsDeferred(request.session.asSession()));
    }

    @Test
    void discardEndsTheSession() {
        FakeRequest request = new FakeRequest(new FakeSession());

        SessionLifecycle.discard(request.asServletRequest());

        assertFalse(request.session.valid);
    }

    /** Fail-closed: a logout that could not reach the server still ends the browser's session. */
    @Test
    void discardIsIdempotent() {
        FakeRequest request = new FakeRequest(new FakeSession());

        SessionLifecycle.discard(request.asServletRequest());
        SessionLifecycle.discard(request.asServletRequest());

        assertFalse(request.session.valid);
    }

    @Test
    void nothingThrowsWhenThereIsNoSession() {
        FakeRequest request = new FakeRequest(null);

        SessionLifecycle.deferDiscardToCaller(request.asServletRequest());
        SessionLifecycle.discard(request.asServletRequest());
        assertFalse(SessionLifecycle.discardIsDeferred(null));
    }

    @Test
    void nothingThrowsWhenTheRequestIsNull() {
        SessionLifecycle.deferDiscardToCaller(null);
        SessionLifecycle.discard(null);
    }

    /**
     * A session ended by another request between the check and the call. Reporting "deferred" is
     * the right answer: there is nothing left for anybody to destroy.
     */
    @Test
    void anAlreadyEndedSessionNeedsNoFurtherDiscarding() {
        FakeSession session = new FakeSession();
        session.valid = false;

        assertTrue(SessionLifecycle.discardIsDeferred(session.asSession()));
    }

    @Test
    void deferringOnAnAlreadyEndedSessionIsQuiet() {
        FakeSession session = new FakeSession();
        FakeRequest request = new FakeRequest(session);
        session.valid = false;

        SessionLifecycle.deferDiscardToCaller(request.asServletRequest());

        assertNull(session.attributes.get(SessionLifecycle.DEFERRED_ATTRIBUTE));
    }

    // ------------------------------------------------------------------------------ scaffolding

    /** A request that answers {@code getSession} and nothing else, as T-18's {@code TestRequest} does. */
    private static final class FakeRequest {

        private final FakeSession session;

        FakeRequest(FakeSession session) {
            this.session = session;
        }

        HttpServletRequest asServletRequest() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class},
                    // Returns the session even after it has been invalidated, which is exactly
                    // the state these methods have to survive: a container hands back the object
                    // it has, and every call on it throws.
                    (target, method, args) -> "getSession".equals(method.getName())
                            ? (session == null ? null : session.asSession())
                            : null);
        }
    }

    private static final class FakeSession {

        private final Map<String, Object> attributes = new HashMap<>();
        private boolean valid = true;

        HttpSession asSession() {
            return (HttpSession) Proxy.newProxyInstance(
                    HttpSession.class.getClassLoader(),
                    new Class<?>[]{HttpSession.class},
                    (target, method, args) -> {
                        switch (method.getName()) {
                            case "setAttribute":
                                requireValid();
                                attributes.put((String) args[0], args[1]);
                                return null;
                            case "getAttribute":
                                requireValid();
                                return attributes.get(args[0]);
                            case "invalidate":
                                requireValid();
                                valid = false;
                                attributes.clear();
                                return null;
                            default:
                                return null;
                        }
                    });
        }

        private void requireValid() {
            if (!valid) {
                throw new IllegalStateException("Session already invalidated");
            }
        }
    }
}
