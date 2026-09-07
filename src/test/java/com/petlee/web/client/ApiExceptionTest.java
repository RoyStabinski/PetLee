package com.petlee.web.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The named branches T-26, T-30, T-31 and T-32 are written against.
 *
 * <p>They exist so a managed bean never writes {@code e.getStatus() == 404}. The point of these
 * assertions is that exactly one predicate answers {@code true} for each status: a bean that takes
 * the "not found" branch on a 409 would silently show the wrong message, and nothing about the
 * running application would look broken.
 */
@DisplayName("ApiException")
class ApiExceptionTest {

    @Test
    void carriesTheStatusCodeAndMessage() {
        ApiException failure = new ApiException(409, "STALE_PET", "This listing was changed.");

        assertEquals(409, failure.getStatus());
        assertEquals("STALE_PET", failure.getCode());
        assertEquals("This listing was changed.", failure.getMessage());
    }

    @Test
    void keepsTheCauseOfATransportFailure() {
        Throwable refused = new IllegalStateException("connection refused");
        ApiException failure = new ApiException(ApiException.TRANSPORT_FAILURE,
                ApiException.API_UNREACHABLE, "The service is not responding.", refused);

        assertSame(refused, failure.getCause());
        assertEquals(503, failure.getStatus());
    }

    @Test
    void namesTheAuthenticationFailure() {
        assertOnly(new ApiException(401, "NOT_AUTHENTICATED", "Please sign in."),
                ApiException::isNotAuthenticated);
    }

    @Test
    void namesTheAuthorisationFailure() {
        assertOnly(new ApiException(403, "NOT_OWNER", "Not yours."), ApiException::isForbidden);
    }

    @Test
    void namesTheMissingResource() {
        assertOnly(new ApiException(404, "PET_NOT_FOUND", "Gone."), ApiException::isNotFound);
    }

    @Test
    void namesTheConflict() {
        assertOnly(new ApiException(409, "USERNAME_TAKEN", "Taken."), ApiException::isConflict);
    }

    @Test
    void namesTheValidationFailure() {
        assertOnly(new ApiException(400, "INVALID_FILTER", "size must be one of ..."),
                ApiException::isValidationFailure);
    }

    /** A 500 matches nothing; the bean shows the message and does not take a special branch. */
    @Test
    void leavesAnUnexpectedStatusUnnamed() {
        ApiException failure = new ApiException(500, "INTERNAL_ERROR", "Something went wrong.");

        assertFalse(failure.isNotAuthenticated());
        assertFalse(failure.isForbidden());
        assertFalse(failure.isNotFound());
        assertFalse(failure.isConflict());
        assertFalse(failure.isValidationFailure());
    }

    @Test
    void printsTheStatusAndCode() {
        assertEquals("ApiException[403 NOT_OWNER]",
                new ApiException(403, "NOT_OWNER", "Not yours.").toString());
    }

    /** Asserts the given predicate holds and that no other one does. */
    private static void assertOnly(ApiException failure,
                                   java.util.function.Predicate<ApiException> expected) {
        assertTrue(expected.test(failure), "the expected predicate should hold");

        int matches = 0;
        matches += failure.isNotAuthenticated() ? 1 : 0;
        matches += failure.isForbidden() ? 1 : 0;
        matches += failure.isNotFound() ? 1 : 0;
        matches += failure.isConflict() ? 1 : 0;
        matches += failure.isValidationFailure() ? 1 : 0;
        assertEquals(1, matches, "exactly one predicate should hold for " + failure);
    }
}
