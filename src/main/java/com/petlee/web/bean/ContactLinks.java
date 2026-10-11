package com.petlee.web.bean;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The links behind the contact card's Call, Email and WhatsApp buttons. Each method answers null
 * when there is nothing to link to, and the page hides that button.
 */
final class ContactLinks {

    private ContactLinks() {
    }

    /**
     * @param phone the owner's phone number as stored, may be null or blank
     * @return a {@code tel:} link to it, or null
     */
    static String call(String phone) {
        return isBlank(phone) ? null : "tel:" + phone.trim();
    }

    /**
     * @param email   the owner's email address, may be null or blank
     * @param petName the listing's name, for the subject line
     * @return a {@code mailto:} link with the subject "About &lt;pet name&gt; on Pet-Lee", or null
     */
    static String email(String email, String petName) {
        if (isBlank(email)) {
            return null;
        }
        return "mailto:" + email.trim() + "?subject=" + encode("About " + petName + " on Pet-Lee");
    }

    /**
     * A WhatsApp chat link, for mobile numbers only: a landline cannot receive WhatsApp. The
     * stored {@code 05X-XXXXXXX} loses its leading 0 and gains the country code 972.
     *
     * @param phone   the owner's phone number as stored, may be null or blank
     * @param petName the listing's name, for the opening message
     * @return {@code https://wa.me/9725XXXXXXXX?text=...}, or null unless the number is a 05X one
     */
    static String whatsApp(String phone, String petName) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (!digits.matches("05\\d{8}")) {
            return null;
        }
        return "https://wa.me/972" + digits.substring(1) + "?text="
                + encode("Hi, I saw " + petName + " on Pet-Lee and I'd like to know more.");
    }

    /**
     * Percent-encodes a query value. URLEncoder writes a space as {@code +}, which a mailto
     * subject shows literally, so it becomes {@code %20}.
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
