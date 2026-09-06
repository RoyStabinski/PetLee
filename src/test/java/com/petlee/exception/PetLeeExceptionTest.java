package com.petlee.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T-12 acceptance criteria: the code is carried, and it is never null. */
class PetLeeExceptionTest {

    @Test
    @DisplayName("criterion 2: the two-argument constructor carries the code through")
    void carriesTheGivenCode() {
        ConflictException e = new ConflictException("USERNAME_TAKEN", "Username already in use");

        assertEquals("USERNAME_TAKEN", e.getCode());
        assertEquals("Username already in use", e.getMessage());
    }

    @Test
    @DisplayName("criterion 3: the one-argument constructor derives a code from the class name")
    void derivesADefaultCodeFromTheClassName() {
        assertEquals("NOT_FOUND", new NotFoundException("no such pet").getCode());
        assertEquals("CONFLICT", new ConflictException("clash").getCode());
        assertEquals("FORBIDDEN", new ForbiddenException("no").getCode());
        assertEquals("UNAUTHORIZED", new UnauthorizedException("who?").getCode());
        assertEquals("VALIDATION", new ValidationException("bad").getCode());
    }

    @Test
    @DisplayName("criterion 3: a blank or null code falls back to the derived one")
    void neverExposesANullOrBlankCode() {
        assertEquals("CONFLICT", new ConflictException(null, "clash").getCode());
        assertEquals("CONFLICT", new ConflictException("   ", "clash").getCode());
        assertEquals("NOT_FOUND", new NotFoundException(null, "gone", new RuntimeException()).getCode());
    }

    @Test
    @DisplayName("every exception is unchecked, so no layer needs a throws clause")
    void isUnchecked() {
        assertTrue(RuntimeException.class.isAssignableFrom(PetLeeException.class));
    }

    @Test
    @DisplayName("the cause is kept for the log; T-19 never puts it in a response")
    void keepsTheCause() {
        Throwable cause = new IllegalStateException("constraint violation");
        ConflictException e = new ConflictException("EMAIL_TAKEN", "Email already in use", cause);

        assertSame(cause, e.getCause());
    }

    @Test
    @DisplayName("ValidationException names the offending field, or leaves it null")
    void carriesAnOptionalFieldName() {
        ValidationException withField =
                new ValidationException("password", "PASSWORD_TOO_SHORT", "Password must be at least 8 characters");
        assertEquals("password", withField.getField());
        assertEquals("PASSWORD_TOO_SHORT", withField.getCode());

        ValidationException withoutField = new ValidationException("Nothing to upload");
        assertNull(withoutField.getField());
        assertNotNull(withoutField.getCode());
    }
}
