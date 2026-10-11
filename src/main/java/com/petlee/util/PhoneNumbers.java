package com.petlee.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Israeli telephone numbers: the one rule registration and profile updates share.
 *
 * <p>Accepted are mobile numbers (05X and seven digits), the 07X numbers (07X and seven digits)
 * and the landlines 02, 03, 04, 08 and 09 (and seven digits). Spaces, dashes and parentheses may
 * appear anywhere, and the leading 0 may be written as {@code +972} or {@code 972}. A number is
 * stored as {@code 0XX-XXXXXXX} (mobile and 07X) or {@code 0X-XXXXXXX} (the other landlines), so
 * the same number never sits in the database in two spellings.
 *
 * <p>The Faces pages carry an equivalent regular expression for a quick answer in the browser;
 * this class is the one that decides.
 */
public final class PhoneNumbers {

    /** What the pages and the API say when a number is refused. */
    public static final String INVALID_MESSAGE =
            "Enter a valid Israeli phone number, for example 050-1234567";

    /** The separators tolerated between digits. */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s()\\-]");

    /**
     * Applied once the separators are gone. Group 1 and 2 are a mobile or 07X number's prefix
     * and subscriber digits, group 3 and 4 a landline's.
     */
    private static final Pattern NUMBER =
            Pattern.compile("(?:\\+972|972|0)(?:([57]\\d)(\\d{7})|([23489])(\\d{7}))");

    private PhoneNumbers() {
    }

    /**
     * Normalises an Israeli number to the form it is stored in.
     *
     * @param raw the number as typed, may be null
     * @return {@code 0XX-XXXXXXX} or {@code 0X-XXXXXXX}, or null if the value is null, blank or
     *         not a valid Israeli number
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(SEPARATORS.matcher(raw).replaceAll(""));
        if (!m.matches()) {
            return null;
        }
        return m.group(1) != null
                ? "0" + m.group(1) + "-" + m.group(2)
                : "0" + m.group(3) + "-" + m.group(4);
    }

    /**
     * @param raw the number as typed, may be null
     * @return whether {@link #normalize} accepts it; false for null and blank
     */
    public static boolean isValid(String raw) {
        return normalize(raw) != null;
    }
}
