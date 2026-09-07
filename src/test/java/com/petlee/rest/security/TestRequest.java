package com.petlee.rest.security;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Proxy;

/**
 * An {@link HttpServletRequest} that implements exactly two things — {@code getSession} and
 * {@code changeSessionId} — and records what was asked of it.
 *
 * <p>{@code HttpServletRequest} declares some seventy methods, and a hand-written stub would be
 * seventy lines of noise around the three that matter. A {@link Proxy} answers the rest with a
 * type-appropriate default, which is also what makes an accidental call visible: a test that
 * depended on {@code getRemoteAddr()} would get {@code null}, not a plausible-looking value.
 *
 * <p>The session-creation counter is what T-18 criterion 8 asserts on: no anonymous request may
 * create a session.
 */
final class TestRequest {

    private FakeHttpSession session;
    private int sessionsCreated;
    private final HttpServletRequest proxy;

    private TestRequest(FakeHttpSession initial) {
        this.session = initial;
        this.proxy = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (target, method, args) -> {
                    if ("changeSessionId".equals(method.getName())) {
                        return changeSessionId();
                    }
                    if (!"getSession".equals(method.getName())) {
                        return defaultValue(method.getReturnType());
                    }
                    boolean create = args == null || args.length == 0 || (Boolean) args[0];
                    return getSession(create);
                });
    }

    /** A request arriving with no cookie: the state of every guest and every crawler. */
    static TestRequest withoutSession() {
        return new TestRequest(null);
    }

    /** A request arriving with a live session, empty of attributes. */
    static TestRequest withSession() {
        return new TestRequest(new FakeHttpSession());
    }

    /** A request arriving with a live session that already holds {@code user}. */
    static TestRequest loggedInAs(SessionUser user) {
        TestRequest request = withSession();
        request.session.setAttribute(CurrentUser.SESSION_ATTRIBUTE, user);
        return request;
    }

    HttpServletRequest asServletRequest() {
        return proxy;
    }

    FakeHttpSession session() {
        return session;
    }

    int sessionsCreated() {
        return sessionsCreated;
    }

    /**
     * The Servlet contract for {@code changeSessionId()}: a new identifier for the session already
     * associated with this request, and an {@link IllegalStateException} when there is none.
     */
    private String changeSessionId() {
        FakeHttpSession current = getSession(false);
        if (current == null) {
            throw new IllegalStateException("no session is associated with this request");
        }
        return current.rotateId();
    }

    private FakeHttpSession getSession(boolean create) {
        if (session != null && !session.isValid()) {
            // A container drops an invalidated session: the next getSession(false) returns null.
            session = null;
        }
        if (session == null && create) {
            session = new FakeHttpSession();
            sessionsCreated++;
        }
        return session;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        return 0;
    }
}
