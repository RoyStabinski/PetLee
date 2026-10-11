package com.petlee.service;

import com.petlee.dto.FocusForm;

/**
 * A photo's focal point: the spot a cropped view centres on, in whole percent of the width
 * ({@code x}) and the height ({@code y}). Views apply it with CSS {@code object-position}; the
 * image file itself is never changed.
 *
 * @param x percent of the width, 0 to 100
 * @param y percent of the height, 0 to 100
 */
public record FocalPoint(int x, int y) {

    /** The centre, which every photo has until its owner moves it. */
    public static final FocalPoint CENTRE = new FocalPoint(50, 50);

    /** The canonical constructor refuses out-of-range values too, so no invalid one exists. */
    public FocalPoint {
        requireInRange("x", x);
        requireInRange("y", y);
    }

    /**
     * Validates a request body.
     *
     * @param form the body as JSON-B read it, null when the request had none
     * @return the focal point
     * @throws AppException 400 if the body, or either value, is missing or out of range
     */
    public static FocalPoint from(FocusForm form) {
        if (form == null) {
            throw new AppException(400, "A request body {\"x\": 0-100, \"y\": 0-100} is required");
        }
        return of(form.x(), form.y());
    }

    /**
     * Validates the values of a request body.
     *
     * @param x the {@code x} the client sent, null when it sent none
     * @param y the {@code y} the client sent, null when it sent none
     * @return the focal point
     * @throws AppException 400 if either value is missing or outside 0 to 100
     */
    public static FocalPoint of(Integer x, Integer y) {
        if (x == null || y == null) {
            throw new AppException(400, "x and y are both required, whole numbers from 0 to 100");
        }
        return new FocalPoint(x, y);
    }

    private static void requireInRange(String name, int value) {
        if (value < 0 || value > 100) {
            throw new AppException(400, name + " must be a whole number from 0 to 100");
        }
    }
}
