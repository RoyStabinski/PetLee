package com.petlee.rest;

import com.petlee.exception.ValidationException;

import java.util.Arrays;
import java.util.List;

/**
 * The two query-parameter conversions {@link PetResource} and {@link AdminResource} share.
 *
 * <h2>Why not {@code @QueryParam("size") Pet.PetSize}</h2>
 * Jakarta REST answers a conversion failure on a {@code @QueryParam} with <strong>404</strong>,
 * which for {@code ?size=HUGE} would say the collection does not exist — the caller would look for
 * a routing problem and never find the typo. Converting by hand makes it a 400 that names the field
 * and lists the values.
 */
final class RestParams {

    private RestParams() {
    }

    /**
     * @param value the raw {@code categoryId} parameter, may be absent or blank
     * @return the id, or {@code null} for no restriction
     * @throws ValidationException <strong>400</strong> {@code INVALID_FILTER} — not a number
     */
    static Integer categoryId(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException notANumber) {
            throw new ValidationException("categoryId", "INVALID_FILTER",
                    "categoryId must be a whole number");
        }
    }

    /**
     * Converts one filter parameter, or refuses the request.
     *
     * <p>A bad value is a 400 rather than a silently empty result, because an ignored filter looks
     * exactly like "nothing matched" — the caller sees an empty gallery and blames the data.
     * Case and surrounding space are forgiven; the contract's own strings are upper case, and
     * accepting {@code small} hides nothing from anybody.
     */
    static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, trimmed.toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new ValidationException(field, "INVALID_FILTER",
                    field + " must be one of " + String.join(", ", names(type)));
        }
    }

    private static <E extends Enum<E>> List<String> names(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        // An absent parameter and "?size=" mean the same thing: no restriction. Treating the empty
        // string as a value would make a form that submits its unset selects fail with a 400.
        return trimmed.isEmpty() ? null : trimmed;
    }
}
