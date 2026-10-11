package com.petlee.web.bean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Turns a photo's focal point into the inline style that crops around it, for every image the
 * stylesheet draws with {@code object-fit: cover}: {@code style="#{focus.style(x, y)}"}. The
 * file is never changed; only the part of it a cropped frame shows moves.
 */
@Named("focus")
@ApplicationScoped
public class FocusBean {

    /**
     * @param x percent of the width, may be null
     * @param y percent of the height, may be null
     * @return {@code object-position: X% Y%}, or null (no style, so the centre) when either is
     *         missing
     */
    public String style(Integer x, Integer y) {
        return styleOf(x, y);
    }

    static String styleOf(Integer x, Integer y) {
        if (x == null || y == null) {
            return null;
        }
        return "object-position: " + clamp(x) + "% " + clamp(y) + "%";
    }

    /** The API never sends a value outside 0 to 100; this only keeps the CSS sane if it did. */
    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
