package com.petlee.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The allow-list, which is the whole of this filter's policy.
 *
 * <p>The redirecting and forwarding need a container and are demonstrated against one. What is
 * worth pinning here is the decision itself, and in particular its <em>default</em>: a page nobody
 * has thought about must come out protected. A deny-list would make every page added later public
 * until somebody remembered, and nothing would report it.
 */
@DisplayName("PageAccessFilter's allow-list")
class PageAccessFilterTest {

    @ParameterizedTest
    @DisplayName("the four public views are public")
    @ValueSource(strings = {"/index.xhtml", "/petDetails.xhtml", "/login.xhtml", "/register.xhtml"})
    void publicViews(String view) {
        assertTrue(PageAccessFilter.isPublic(view));
    }

    @ParameterizedTest
    @DisplayName("error pages and resources are public, or an error page would need a login")
    @ValueSource(strings = {
            "/error/403.xhtml", "/error/404.xhtml", "/error/500.xhtml", "/error/expired.xhtml",
            "/resources/css/petlee.css", "/resources/images/placeholder-pet.png"})
    void publicPrefixes(String view) {
        assertTrue(PageAccessFilter.isPublic(view));
    }

    @ParameterizedTest
    @DisplayName("the authenticated and admin pages are not")
    @ValueSource(strings = {"/profile.xhtml", "/addPet.xhtml", "/editPet.xhtml", "/admin.xhtml"})
    void protectedViews(String view) {
        assertFalse(PageAccessFilter.isPublic(view));
    }

    /**
     * The point of the allow-list. Nobody has heard of these views; every one of them is protected.
     */
    @ParameterizedTest
    @DisplayName("a page nobody has thought about is protected by default")
    @ValueSource(strings = {
            "/reports.xhtml", "/settings.xhtml", "/moderation/queue.xhtml", "/newFeature.xhtml"})
    void unknownViewsAreProtected(String view) {
        assertFalse(PageAccessFilter.isPublic(view));
    }

    /**
     * A near-miss must not inherit a public view's status. {@code /index.xhtml.bak} and
     * {@code /error} are not the pages they resemble.
     */
    @ParameterizedTest
    @DisplayName("a near-miss is not public")
    @ValueSource(strings = {
            "/index.xhtml.bak", "/login.xhtml/extra", "/error", "/resources", "/xerror/403.xhtml"})
    void nearMissesAreProtected(String view) {
        assertFalse(PageAccessFilter.isPublic(view));
    }
}
