package com.petlee.rest.mapper;

import com.petlee.dto.ErrorDTO;
import com.petlee.service.AppException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The one place a failure that escapes a resource method becomes an HTTP response, for every
 * {@link Throwable} that is not a {@link jakarta.validation.ConstraintViolationException}.
 *
 * <h2>What it replaces</h2>
 * Four files this task collapses into one: a mapper for the business layer's own exception, a
 * broader mapper that classified everything else (routing failures, an escaped optimistic lock, a
 * wrapped JSON parse failure), a third mapper for the unwrapped case of that same parse failure,
 * and a small response-building helper the other three shared. One
 * {@code ExceptionMapper<Throwable>} handles the same cases because Jakarta REST does not pick a
 * mapper by how many exist — it picks the one whose type is nearest the thrown exception, and with
 * only one registered, that one runs every time.
 *
 * <h2>Why {@code ConstraintViolationException} is not in the table below</h2>
 * It used to be, but "nearest declared type wins" cuts the other way for that one exception: a
 * JAX-RS implementation's own built-in bean-validation mapper is a nearer match to
 * {@code ConstraintViolationException} than {@code ExceptionMapper<Throwable>} is, so it always
 * won over a branch in here — confirmed live as a bare Payara HTML error page instead of this
 * application's JSON. {@link ConstraintViolationExceptionMapper} declares the exact generic type
 * instead, which out-ranks the container's built-in mapper the same way this class out-ranks it
 * for everything else. Do not re-add a branch for it here: it would be correct code that never
 * runs.
 *
 * <h2>The table</h2>
 * <ul>
 *   <li>{@link AppException} — the status it already carries, via {@link AppException#getStatus()}.
 *       A service throws this and names its own status; there is no table to keep in sync here.</li>
 *   <li>{@link WebApplicationException} — the status it already carries (a 404 from routing, a 405
 *       from a wrong method, a 415 from a wrong content type). Rewriting those to 500 would report
 *       the server's own routing as broken.</li>
 *   <li>A {@code jakarta.json.bind.JsonbException} or {@code jakarta.json.JsonException}, anywhere
 *       in the cause chain — a request body JSON-B could not parse into the target record. 400,
 *       not 500: the caller sent bad input, the server did nothing wrong.</li>
 *   <li>Everything else — 500, with no detail in the body. This is the only place an unexpected
 *       failure is recorded; the response carries no stack trace, class name or SQL fragment,
 *       because any of those tells an attacker the framework, its version and the call path.</li>
 * </ul>
 */
@Provider
public class ErrorMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(ErrorMapper.class.getName());

    @Override
    public Response toResponse(Throwable failure) {
        if (failure instanceof AppException app) {
            return json(app.getStatus(), "ERROR", app.getMessage());
        }
        if (failure instanceof WebApplicationException web) {
            return json(web.getResponse().getStatus(), "ERROR", web.getMessage());
        }
        if (isMalformedJson(failure)) {
            // A body that JSON-B cannot parse into the target record is the caller's mistake, not
            // the server's — 400, not 500, and no parser internals in the message.
            return json(400, "MALFORMED_JSON", "The request body is not valid JSON.");
        }
        // The only place an unexpected failure is recorded. The response carries no detail:
        // a stack trace in an HTTP body tells an attacker the framework, the version and the
        // call path.
        LOGGER.log(Level.SEVERE, "Unhandled failure", failure);
        return json(500, "INTERNAL_ERROR", "Something went wrong. Please try again.");
    }

    /**
     * Whether {@code failure} or anything in its cause chain is a JSON-B or JSON-P parse failure —
     * a malformed request body, walking up through however many wrapper exceptions the container's
     * message-body-reader machinery adds on top of it.
     */
    private static boolean isMalformedJson(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof jakarta.json.bind.JsonbException
                    || cause instanceof jakarta.json.JsonException) {
                return true;
            }
        }
        return false;
    }

    private static Response json(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO(code, message)).build();
    }
}
