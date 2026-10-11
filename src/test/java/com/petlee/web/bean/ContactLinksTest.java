package com.petlee.web.bean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContactLinksTest {

    private static final String TEXT =
            "?text=Hi%2C%20I%20saw%20Rex%20on%20Pet-Lee%20and%20I%27d%20like%20to%20know%20more.";

    @ParameterizedTest(name = "{0} -> wa.me/{1}")
    @CsvSource(delimiter = '|', textBlock = """
            050-1234567     | 972501234567
            054-9876543     | 972549876543
            058-0000001     | 972580000001
            0521234567      | 972521234567
            """)
    @DisplayName("A mobile number links to WhatsApp with 972 in place of the leading 0")
    void should_link_to_whatsapp_when_number_is_mobile(String phone, String international) {
        assertEquals("https://wa.me/" + international + TEXT, ContactLinks.whatsApp(phone, "Rex"));
    }

    @ParameterizedTest(name = "\"{0}\" has no WhatsApp link")
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "02-1234567",       // Jerusalem landline
            "03-1234567",       // Tel Aviv landline
            "04-1234567",
            "08-1234567",
            "09-1234567",
            "077-1234567",      // 07X numbers are not mobiles
            "050-123456"        // one digit short
    })
    @DisplayName("A landline, an empty or a missing number has no WhatsApp link")
    void should_have_no_whatsapp_link_when_number_is_not_mobile(String phone) {
        assertNull(ContactLinks.whatsApp(phone, "Rex"));
    }

    @Test
    @DisplayName("The WhatsApp message URL-encodes the pet's name")
    void should_encode_pet_name_when_it_has_special_characters() {
        assertEquals("https://wa.me/972501234567?text=Hi%2C%20I%20saw%20Tom%20%26%20Jerry%2B"
                        + "%20on%20Pet-Lee%20and%20I%27d%20like%20to%20know%20more.",
                ContactLinks.whatsApp("050-1234567", "Tom & Jerry+"));
    }

    @Test
    @DisplayName("Call links to the stored number")
    void should_link_to_stored_number_when_calling() {
        assertEquals("tel:03-1234567", ContactLinks.call("03-1234567"));
    }

    @Test
    @DisplayName("Email links to the address with an encoded subject")
    void should_link_with_subject_when_emailing() {
        assertEquals("mailto:dana@example.com?subject=About%20Rex%20on%20Pet-Lee",
                ContactLinks.email("dana@example.com", "Rex"));
    }

    @ParameterizedTest(name = "\"{0}\" gives no Call or Email link")
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("A missing phone or address gives no Call or Email link")
    void should_have_no_link_when_value_is_missing(String value) {
        assertNull(ContactLinks.call(value));
        assertNull(ContactLinks.email(value, "Rex"));
    }
}
