package com.petlee.rest.mapper;

import com.petlee.dto.ErrorDTO;
import com.petlee.service.AppException;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The one place a failure that escapes a resource method becomes an HTTP response.
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
 * <h2>The table</h2>
 * <ul>
 *   <li>{@link AppException} — the status it already carries, via {@link AppException#getStatus()}.
 *       A service throws this and names its own status; there is no table to keep in sync here.</li>
 *   <li>{@link ConstraintViolationException} — 400, naming the first offending field. This is the
 *       one this task adds: {@code @Valid} on service methods raises this, and until now nothing
 *       mapped it, so it fell through to the 500 branch below.</li>
 *   <li>{@link WebApplicationException} — the status it already carries (a 404 from routing, a 405
 *       from a wrong method, a 415 from a wrong content type). Rewriting those to 500 would report
 *       the server's own routing as broken.</li>
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
        if (failure instanceof ConstraintViolationException violations) {
            String message = violations.getConstraintViolations().stream().findFirst()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .orElse("The request is not valid");
            return json(400, "VALIDATION_FAILED", message);
        }
        if (failure instanceof WebApplicationException web) {
            return json(web.getResponse().getStatus(), "ERROR", web.getMessage());
        }
        // The only place an unexpected failure is recorded. The response carries no detail:
        // a stack trace in an HTTP body tells an attacker the framework, the version and the
        // call path.
        LOGGER.log(Level.SEVERE, "Unhandled failure", failure);
        return json(500, "INTERNAL_ERROR", "Something went wrong. Please try again.");
    }

    private static Response json(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO(code, message)).build();
    }
}
