package com.petlee.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhoneNumbersTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            0501234567          | 050-1234567
            050-1234567         | 050-1234567
            050 123 4567        | 050-1234567
            (050) 123-4567      | 050-1234567
            +972501234567       | 050-1234567
            +972-50-123-4567    | 050-1234567
            972 50 123 4567     | 050-1234567
            054-9876543         | 054-9876543
            021234567           | 02-1234567
            03-123-4567         | 03-1234567
            (04) 123 4567       | 04-1234567
            +972 8 123 4567     | 08-1234567
            9729-1234567        | 09-1234567
            077-1234567         | 077-1234567
            +972 73 123 4567    | 073-1234567
            """)
    @DisplayName("A valid Israeli number is accepted and normalised")
    void should_normalise_when_number_is_valid(String raw, String expected) {
        assertEquals(expected, PhoneNumbers.normalize(raw));
    }

    @ParameterizedTest(name = "\"{0}\" is refused")
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "050123456",        // too short: six subscriber digits
            "05012345678",      // too long: eight subscriber digits
            "02123456",         // landline too short
            "0212345678",       // landline too long
            "0612345678",       // 06 is not an Israeli prefix
            "011234567",        // 01 is not an Israeli prefix
            "+9710501234567",   // another country's code
            "050-123456a",      // a letter
            "phone",            // only letters
            "50-1234567"        // missing the leading 0
    })
    @DisplayName("An invalid number is refused")
    void should_refuse_when_number_is_invalid(String raw) {
        assertNull(PhoneNumbers.normalize(raw));
    }
}
