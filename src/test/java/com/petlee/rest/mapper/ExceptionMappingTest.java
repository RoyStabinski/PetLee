package com.petlee.rest.mapper;

import com.petlee.exception.ConflictException;
import com.petlee.exception.ForbiddenException;
import com.petlee.exception.NotFoundException;
import com.petlee.exception.PetLeeException;
import com.petlee.exception.UnauthorizedException;
import com.petlee.exception.ValidationException;

import jakarta.json.bind.JsonbException;
import jakarta.persistence.OptimisticLockException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

/**
 * The status table and the classification rules, asserted directly.
 *
 * <p>{@code toResponse} is not called: building a {@code Response} needs a Jakarta REST runtime,
 * which is a platform service and, under ADR-003, not a test dependency. So each mapper exposes
 * its decision — {@code statusFor}, {@code classify}, {@code fromStatus} — and the encoding of
 * that decision is proven against a deployed server, which is what T-19's acceptance criteria ask
 * for anyway.
 */
class ExceptionMappingTest {

    @Nested
    @DisplayName("PetLeeExceptionMapper — the contract's status table")
    class DomainExceptions {

        @Test
        @DisplayName("each domain exception maps to the status api-contract.md promises")
        void table() {
            assertEquals(400, PetLeeExceptionMapper.statusFor(
                    new ValidationException("password", "TOO_SHORT", "Password is too short")));
            assertEquals(401, PetLeeExceptionMapper.statusFor(
                    new UnauthorizedException("BAD_CREDENTIALS", "Username or password is incorrect")));
            assertEquals(403, PetLeeExceptionMapper.statusFor(
                    new ForbiddenException("This listing belongs to someone else")));
            assertEquals(404, PetLeeExceptionMapper.statusFor(
                    new NotFoundException("No such pet")));
            assertEquals(409, PetLeeExceptionMapper.statusFor(
                    new ConflictException("USERNAME_TAKEN", "That username is taken")));
        }

        @Test
        @DisplayName("a subclass with no row in the table is a 500, not a guessed 400")
        void unmappedSubclassIsInternal() {
            PetLeeException unknown = new PetLeeException("Something new") {
            };

            assertEquals(500, PetLeeExceptionMapper.statusFor(unknown));
        }

        @Test
        @DisplayName("the code reaching the client is the exception's own")
        void codesAreVerbatim() {
            // The client branches on these; T-24 and the JSF beans compare them as strings.
            assertEquals("USERNAME_TAKEN",
                    new ConflictException("USERNAME_TAKEN", "taken").getCode());
            assertEquals("NOT_FOUND", new NotFoundException("gone").getCode());
        }
    }

    @Nested
    @DisplayName("GenericExceptionMapper — what is not an internal error")
    class Fallback {

        @Test
        @DisplayName("criterion 5 — an unexpected failure is a 500 with no detail in the body")
        void unexpectedFailure() {
            GenericExceptionMapper.Outcome outcome =
                    GenericExceptionMapper.classify(new RuntimeException("boom"));

            assertEquals(500, outcome.status);
            assertEquals("INTERNAL_ERROR", outcome.code);
            // null means the body comes from ErrorResponses.internalError, which contains the
            // fixed sentence and a reference and nothing else. "boom" must not travel.
            assertNull(outcome.message);
            assertFalse(outcome.logDetail.contains("boom"),
                    "even the log line's summary quotes no message; the stack trace carries it");
        }

        @Test
        @DisplayName("requirement 7 — a WebApplicationException keeps its status")
        void webApplicationExceptionKeepsStatus() {
            GenericExceptionMapper.Outcome notFound = GenericExceptionMapper.fromStatus(404);
            assertEquals(404, notFound.status);
            assertEquals("NOT_FOUND", notFound.code);
            assertEquals("Not Found", notFound.message);

            GenericExceptionMapper.Outcome methodNotAllowed = GenericExceptionMapper.fromStatus(405);
            assertEquals(405, methodNotAllowed.status);
            assertEquals("METHOD_NOT_ALLOWED", methodNotAllowed.code);
        }

        @Test
        @DisplayName("a status Response.Status does not name still keeps its number")
        void unknownStatusCode() {
            GenericExceptionMapper.Outcome outcome = GenericExceptionMapper.fromStatus(418);

            assertEquals(418, outcome.status);
            assertEquals("HTTP_418", outcome.code);
        }

        @Test
        @DisplayName("requirement 8 — an optimistic lock escaping the service layer is still 409")
        void optimisticLockIsConflict() {
            // How it actually arrives: the provider raises it at flush, and the transaction
            // manager wraps it on the way out of the resource method.
            Throwable wrapped = new IllegalStateException("commit failed",
                    new OptimisticLockException("Row was updated by another transaction"));

            GenericExceptionMapper.Outcome outcome = GenericExceptionMapper.classify(wrapped);

            assertEquals(409, outcome.status);
            assertEquals("STALE_PET", outcome.code,
                    "the same code T-15 uses, so a client cannot tell which layer caught it");
        }

        @Test
        @DisplayName("criterion 4 — a wrapped JSON parse failure is still 400 MALFORMED_JSON")
        void wrappedJsonFailure() {
            Throwable wrapped = new RuntimeException("reading entity",
                    new JsonbException("Unexpected char at position 3"));

            GenericExceptionMapper.Outcome outcome = GenericExceptionMapper.classify(wrapped);

            assertEquals(400, outcome.status);
            assertEquals("MALFORMED_JSON", outcome.code);
            assertEquals("The request body is not valid JSON.", outcome.message,
                    "the parser's own message quotes the caller's input back at them");
        }

        @Test
        @DisplayName("a cause chain that loops back on itself terminates")
        void cyclicCauseChain() {
            RuntimeException first = new RuntimeException("first");
            Throwable second = new RuntimeException("second") {
                @Override
                public synchronized Throwable getCause() {
                    return first;
                }
            };
            first.initCause(second);

            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertEquals(500, GenericExceptionMapper.classify(first).status));
        }
    }

    @Nested
    @DisplayName("correlation ids")
    class CorrelationIds {

        @Test
        @DisplayName("are eight unambiguous characters")
        void shape() {
            String id = ErrorResponses.newCorrelationId();

            assertEquals(8, id.length());
            // I, O, 0 and 1 are excluded: the id is read off a screen and typed into a log search.
            assertTrue(id.chars().noneMatch(c -> "IO01".indexOf(c) >= 0), id);
            assertTrue(id.chars().allMatch(c -> Character.isUpperCase(c) || Character.isDigit(c)), id);
        }

        @Test
        @DisplayName("differ between failures, so two reports do not collide")
        void distinct() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                ids.add(ErrorResponses.newCorrelationId());
            }

            // Not a uniqueness guarantee - 40 bits is not that - but a collision in 200 draws
            // would mean the generator is not random at all.
            assertTrue(ids.size() > 195, "generated only " + ids.size() + " distinct ids");
        }
    }
}
