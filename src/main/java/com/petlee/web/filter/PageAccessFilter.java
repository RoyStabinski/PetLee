package com.petlee.web.filter;

import com.petlee.web.bean.UserBean;

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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps guests off the pages that are not for them, by allow-list, so a page added later is
 * protected by default.
 *
 * <p>Convenience, not enforcement: the services and {@code @Secured} already refuse the
 * operations behind these pages, and no REST check may be weakened because this filter exists.
 */
@WebFilter(urlPatterns = "*.xhtml")
public class PageAccessFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(PageAccessFilter.class.getName());

    /** Reachable by anyone. The gallery and the detail page are here by design. */
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
    private static final String FORBIDDEN_VIEW = "/error/error.xhtml";

    @Inject
    private UserBean userBean;

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
            // A typo is not worth a login prompt. Let Faces answer 404.
            chain.doFilter(request, response);
            return;
        }

        if (!userBean.isLoggedIn()) {
            LOGGER.log(Level.FINE, () -> "guest redirected from " + view + " to the login page");
            httpResponse.sendRedirect(http.getContextPath() + LOGIN_VIEW);
            return;
        }

        if (ADMIN_VIEWS.contains(view) && !userBean.isAdmin()) {
            // Not a redirect to login: they are logged in, and logging in again would not help.
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
            LOGGER.log(Level.FINE, "unparseable view path " + view, impossible);
            return false;
        }
    }

    /**
     * @param request the request
     * @return the view asked for, starting with {@code /} and without the context path
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
