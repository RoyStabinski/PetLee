package com.petlee.config;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies the {@code Secure} flag to the session cookie from a context parameter, at start-up.
 *
 * <h2>Why this class exists at all</h2>
 * {@code web.xml}'s {@code <cookie-config><secure>} takes a literal, and a deployment descriptor
 * has no way to read a variable. But the correct value differs by environment and cannot be
 * guessed: a {@code Secure} cookie is not sent over plain HTTP, so hard-coding {@code true} makes
 * local development look as though login succeeds and is then instantly forgotten, while
 * hard-coding {@code false} ships a session cookie that a TLS deployment will happily leak over
 * any accidental HTTP request. Reading {@code petlee.session.cookie.secure} here turns that into a
 * one-line change in {@code web.xml} — or a server-level override — which is what T-42's runbook
 * documents.
 *
 * <h2>Why a listener and not a filter</h2>
 * {@link SessionCookieConfig} may only be modified before the servlet context has finished
 * starting; {@code contextInitialized} is the last moment it is legal, and every later attempt
 * throws {@code IllegalStateException}. A filter would run far too late.
 *
 * <h2>Scope</h2>
 * {@code HttpOnly} is not set here. It is declared in {@code web.xml}, where it is visible to
 * anyone reading the descriptor, and it is unconditional — no environment wants it off.
 */
@WebListener
public class SessionCookieConfigurator implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(SessionCookieConfigurator.class.getName());

    /** The {@code web.xml} context parameter this listener reads. */
    public static final String SECURE_COOKIE_PARAMETER = "petlee.session.cookie.secure";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();

        // Absent or unparseable means false. That is the safe default in the only sense that
        // matters here: it fails towards a working login on http://localhost, and the deployment
        // that needs 'true' is the one with a runbook telling it to set the parameter.
        boolean secure = Boolean.parseBoolean(context.getInitParameter(SECURE_COOKIE_PARAMETER));

        SessionCookieConfig cookie = context.getSessionCookieConfig();
        cookie.setSecure(secure);

        LOGGER.log(Level.INFO, () -> "Session cookie: HttpOnly=" + cookie.isHttpOnly()
                + ", Secure=" + cookie.isSecure()
                + " (from " + SECURE_COOKIE_PARAMETER + ")");
    }
}
