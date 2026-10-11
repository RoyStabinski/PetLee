package com.petlee.web.bean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FocusBeanTest {

    @Test
    @DisplayName("A focal point becomes an object-position in percent")
    void should_render_object_position_when_focus_is_given() {
        assertEquals("object-position: 30% 70%", FocusBean.styleOf(30, 70));
        assertEquals("object-position: 0% 100%", FocusBean.styleOf(0, 100));
    }

    @Test
    @DisplayName("A missing value gives no style, so the photo stays centred")
    void should_render_no_style_when_a_value_is_missing() {
        assertNull(FocusBean.styleOf(null, 50));
        assertNull(FocusBean.styleOf(50, null));
    }
}
