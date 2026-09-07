package com.petlee.rest.security;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A session that behaves like a container's in the two ways these tests depend on: it has an
 * identifier that changes when a new one is created, and it refuses every operation once
 * invalidated.
 *
 * <p>Written by hand rather than mocked because ADR-003 keeps the dependency list closed — JUnit
 * is the only test library — and because the invalidation behaviour is the thing under test, not
 * incidental scaffolding.
 */
final class FakeHttpSession implements HttpSession {

    private static int counter;

    private final String id = "SESSION-" + (++counter);
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private boolean valid = true;
    private int maxInactiveInterval = 1800;

    boolean isValid() {
        return valid;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void invalidate() {
        requireValid();
        valid = false;
        attributes.clear();
    }

    @Override
    public Object getAttribute(String name) {
        requireValid();
        return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        requireValid();
        attributes.put(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        requireValid();
        attributes.remove(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        requireValid();
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public long getCreationTime() {
        requireValid();
        return 0L;
    }

    @Override
    public long getLastAccessedTime() {
        requireValid();
        return 0L;
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        this.maxInactiveInterval = interval;
    }

    @Override
    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    public boolean isNew() {
        requireValid();
        return true;
    }

    /** What a real container does: every method but a few throws once the session is gone. */
    private void requireValid() {
        if (!valid) {
            throw new IllegalStateException("Session " + id + " has been invalidated");
        }
    }
}
