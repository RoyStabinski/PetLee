package com.petlee.service;

import com.petlee.dto.FocusForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FocalPointTest {

    @ParameterizedTest(name = "x={0}, y={1} is accepted")
    @CsvSource({"0, 0", "100, 100", "0, 100", "100, 0", "50, 50", "1, 99"})
    @DisplayName("Whole percentages from 0 to 100 are accepted as they are")
    void should_accept_when_both_values_are_within_zero_to_one_hundred(int x, int y) {
        FocalPoint focus = FocalPoint.from(new FocusForm(x, y));

        assertEquals(x, focus.x());
        assertEquals(y, focus.y());
    }

    // An empty CSV column is null, which is how JSON-B reads a value the body left out.
    @ParameterizedTest(name = "x={0}, y={1} is refused")
    @CsvSource({
            "-1, 50",
            "101, 50",
            "50, -1",
            "50, 101",
            ", 50",
            "50, ",
            ", "
    })
    @DisplayName("A value below 0, above 100 or missing is a 400")
    void should_refuse_with_400_when_a_value_is_out_of_range_or_missing(Integer x, Integer y) {
        AppException refused = assertThrows(AppException.class,
                () -> FocalPoint.from(new FocusForm(x, y)));

        assertEquals(400, refused.getStatus());
    }

    @Test
    @DisplayName("A request with no body at all is a 400")
    void should_refuse_with_400_when_body_is_missing() {
        AppException refused = assertThrows(AppException.class, () -> FocalPoint.from(null));

        assertEquals(400, refused.getStatus());
    }

    @Test
    @DisplayName("No focal point can be built outside 0 to 100, even directly")
    void should_refuse_when_constructed_out_of_range() {
        assertThrows(AppException.class, () -> new FocalPoint(101, 50));
        assertThrows(AppException.class, () -> new FocalPoint(50, -1));
    }
}
