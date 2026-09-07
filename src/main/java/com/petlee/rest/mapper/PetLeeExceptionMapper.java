package com.petlee.rest.mapper;

import com.petlee.exception.ConflictException;
import com.petlee.exception.ForbiddenException;
import com.petlee.exception.NotFoundException;
import com.petlee.exception.PetLeeException;
import com.petlee.exception.UnauthorizedException;
import com.petlee.exception.ValidationException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns a business-layer failure into the status {@code api-contract.md} promises.
 *
 * <h2>Why this exists</h2>
 * Without it, every resource method would open with a {@code try} and close with five
 * {@code catch} blocks, and the fifth one would eventually be forgotten. With it, a service throws
 * what happened and the status is decided in a single table — which is also the only way the
 * contract's status codes can be reviewed without reading every endpoint.
 *
 * <h2>The table</h2>
 * It is the one documented on {@link PetLeeException}, unchanged:
 * {@link ValidationException} 400, {@link UnauthorizedException} 401, {@link ForbiddenException}
 * 403, {@link NotFoundException} 404, {@link ConflictException} 409.
 *
 * <h2>What reaches the client</h2>
 * The exception's own code and message, verbatim. That is safe by construction rather than by
 * inspection here: {@code PetLeeException}'s contract is that its message is user-facing and names
 * no table, column, file path or user that exists. A cause, where one was attached, is not
 * consulted — the {@code PersistenceException} behind a 409 would name the constraint that
 * rejected the insert.
 *
 * <p>{@link ValidationException#getField()} is logged but not serialised: {@link com.petlee.dto.ErrorDTO}
 * has two fields, code and message, and widening it is a contract change that belongs in ADR-002
 * rather than in a mapper.
 */
@Provider
public class PetLeeExceptionMapper implements ExceptionMapper<PetLeeException> {

    private static final Logger LOGGER = Logger.getLogger(PetLeeExceptionMapper.class.getName());

    @Override
    public Response toResponse(PetLeeException failure) {
        int status = statusFor(failure);

        if (status >= 500) {
            // A subclass added later without a row in the table below. It is treated exactly like
            // any other unexpected failure: SEVERE, a stack trace in the log, and a generic body -
            // because a message written for a 400 may be misleading rather than safe at a 500, and
            // the missing row is a bug to be found rather than a response to be shaped.
            String correlationId = ErrorResponses.newCorrelationId();
            LOGGER.log(Level.SEVERE, failure, () -> "500 [" + correlationId + "] "
                    + failure.getClass().getName() + " has no status mapping");
            return ErrorResponses.internalError(correlationId);
        }

        // FINE, not WARNING. Every one of these is an ordinary client mistake - a wrong password,
        // a stale form, a pet someone else already deleted - and logging them at a level that is
        // on by default turns the server log into a transcript of user errors, which is how the
        // one line that mattered gets missed.
        LOGGER.log(Level.FINE, failure,
                () -> status + " " + failure.getCode() + describeField(failure));

        return ErrorResponses.json(status, failure.getCode(), failure.getMessage());
    }

    /**
     * The status table, as a method so it can be asserted directly.
     *
     * <p>Order matters only in that each test is against a leaf type; the hierarchy is flat, so no
     * subclass can be shadowed by an earlier branch. An unrecognised subclass — one added later
     * without a row here — becomes 500 rather than a plausible-looking 400, because a wrong status
     * silently satisfied is worse than a loud one.
     *
     * @param failure the exception thrown by the service layer
     * @return the HTTP status to answer with
     */
    static int statusFor(PetLeeException failure) {
        if (failure instanceof ValidationException) {
            return Response.Status.BAD_REQUEST.getStatusCode();
        }
        if (failure instanceof UnauthorizedException) {
            return Response.Status.UNAUTHORIZED.getStatusCode();
        }
        if (failure instanceof ForbiddenException) {
            return Response.Status.FORBIDDEN.getStatusCode();
        }
        if (failure instanceof NotFoundException) {
            return Response.Status.NOT_FOUND.getStatusCode();
        }
        if (failure instanceof ConflictException) {
            return Response.Status.CONFLICT.getStatusCode();
        }
        return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    }

    private static String describeField(PetLeeException failure) {
        if (failure instanceof ValidationException validation && validation.getField() != null) {
            return " on field '" + validation.getField() + "'";
        }
        return "";
    }
}
