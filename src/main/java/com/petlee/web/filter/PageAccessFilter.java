package com.petlee.web.filter;

import com.petlee.web.bean.UserManagedBean;

import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps guests off the pages that are not for them, and administrators' pages off everyone else.
 *
 * <h2>This is not the security boundary</h2>
 * <strong>Say it again: this filter is convenience and clarity, not enforcement.</strong> Every
 * operation behind these pages is already checked server-side by T-18 — {@code @Secured} answers
 * 401 to a guest and {@code @AdminOnly} answers 403 to a member, on every request, whatever route
 * it arrives by. What this filter buys is a user who is told to log in instead of watching a page
 * render empty and then fail on its first action.
 *
 * <p>Nobody may weaken a REST check because a page is "already protected here". A page filter
 * protects pages; the API is reachable without ever loading one.
 *
 * <h2>Allow-list, not deny-list</h2>
 * Anything not named public requires a login. The alternative — listing what to protect — makes
 * every page added later public by default, until somebody remembers. That failure is silent, and
 * the person who notices is usually not on the team.
 *
 * <h2>Where "who is signed in" comes from</h2>
 * {@link UserManagedBean}, the web tier's own bean. Not the REST tier's session attribute: reading
 * that from here would be {@code com.petlee.web} reaching into {@code com.petlee.rest}, which
 * inverts the layering ADR-001 exists to protect. The bean is only touched for a page that is not
 * public, so a guest browsing the gallery is never given a session on this filter's account.
 */
@WebFilter(urlPatterns = "*.xhtml")
public class PageAccessFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(PageAccessFilter.class.getName());

    /**
     * Reachable by anyone. Specification §10 scenario 1 is a guest browsing and opening a listing,
     * so the gallery and the detail page are here by design, not by oversight.
     */
    private static final Set<String> PUBLIC_VIEWS = Set.of(
            "/index.xhtml",
            "/petDetails.xhtml",
            "/login.xhtml",
            "/register.xhtml");

    /** Prefixes that are public wholesale: the error pages, and the stylesheet and images. */
    private static final Set<String> PUBLIC_PREFIXES = Set.of("/error/", "/resources/");

    /** Requires an administrator. A member gets 403, not a login prompt. */
    private static final Set<String> ADMIN_VIEWS = Set.of("/admin.xhtml");

    private static final String LOGIN_VIEW = "/login.xhtml";
    private static final String FORBIDDEN_VIEW = "/error/403.xhtml";

    @Inject
    private UserManagedBean userBean;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String view = viewOf(http);

        if (isPublic(view)) {
            chain.doFilter(request, response);
            return;
        }

        if (!viewExists(http, view)) {
            // A page that does not exist is not a page to demand a login for. Guarding it would
            // answer a guest's typo with a login form, tell them nothing, and - once they had
            // signed in - hand them a 404 anyway. Let it through and let Faces answer 404, which
            // web.xml turns into the not-found page.
            chain.doFilter(request, response);
            return;
        }

        if (!userBean.isLoggedIn()) {
            // With the destination attached, so T-26's login() can put them where they were going.
            // Losing it makes the user navigate back by hand after authenticating.
            LOGGER.log(Level.FINE, () -> "guest redirected from " + view + " to the login page");
            httpResponse.sendRedirect(http.getContextPath() + LOGIN_VIEW
                    + "?returnUrl=" + URLEncoder.encode(view, StandardCharsets.UTF_8));
            return;
        }

        if (ADMIN_VIEWS.contains(view) && !userBean.isAdmin()) {
            // Not a redirect to login: they are logged in, and logging in again would not help.
            // Saying "403" is the honest answer and the one that stops them trying.
            LOGGER.log(Level.FINE, () -> "non-administrator refused " + view);
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            http.getRequestDispatcher(FORBIDDEN_VIEW).forward(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * @param request the request being served
     * @param view    the view it asks for
     * @return whether that view is a real file in this application
     */
    private static boolean viewExists(HttpServletRequest request, String view) {
        try {
            return request.getServletContext().getResource(view) != null;
        } catch (MalformedURLException impossible) {
            // The path came from the container's own parse of the request it is serving.
            LOGGER.log(Level.FINE, "unparseable view path " + view, impossible);
            return false;
        }
    }

    /**
     * @param request the request
     * @return the view being asked for, always starting with {@code /} and never carrying the
     *         context path — so the sets above can be written the way the views are named
     */
    private static String viewOf(HttpServletRequest request) {
        String path = request.getServletPath();
        String extra = request.getPathInfo();
        return extra == null ? path : path + extra;
    }

    /**
     * @param view the requested view
     * @return whether it needs no session at all
     */
    static boolean isPublic(String view) {
        if (PUBLIC_VIEWS.contains(view)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (view.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
