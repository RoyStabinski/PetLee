package com.petlee.rest.mapper;

import com.petlee.service.AppException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns a business-layer failure into the status it already carries.
 *
 * <h2>Why this exists</h2>
 * Without it, every resource method would open with a {@code try} and close with a {@code catch}
 * block, and the fifth one would eventually be forgotten. With it, a service throws
 * {@link AppException} and this mapper turns it into a JSON body at the status the exception
 * itself names — the six-class hierarchy this replaced (kept a status per subclass in a table
 * here; {@link AppException} carries its own, so there is no table left to keep in sync).
 *
 * <h2>What reaches the client</h2>
 * The exception's own message, verbatim, and a code derived from the status
 * ({@code Response.Status} name, or {@code HTTP_<code>} for one this runtime does not recognise).
 * That is safe by construction rather than by inspection here: {@link AppException}'s contract,
 * inherited from the six classes it replaced, is that its message is user-facing and names no
 * table, column, file path or user that exists.
 *
 * <p>This mapper does not yet turn a {@code jakarta.validation.ConstraintViolationException} from
 * the new {@code @Valid} service parameters into a 400 — that mapping, and any consolidation of
 * this class with {@link GenericExceptionMapper}, is the REST exception-mapping task's job, not
 * this one's. Until it lands, such a failure reaches {@link GenericExceptionMapper} and becomes a
 * 500, on the REST tier only; the JSF tier's beans catch it directly.
 */
@Provider
public class AppExceptionMapper implements ExceptionMapper<AppException> {

    private static final Logger LOGGER = Logger.getLogger(AppExceptionMapper.class.getName());

    @Override
    public Response toResponse(AppException failure) {
        int status = failure.getStatus();

        if (status >= 500) {
            // A status that names no client error. Treated like any other unexpected failure:
            // SEVERE, a stack trace in the log, and a generic body.
            String correlationId = ErrorResponses.newCorrelationId();
            LOGGER.log(Level.SEVERE, failure, () -> "500 [" + correlationId + "] "
                    + failure.getClass().getName() + " carried status " + status);
            return ErrorResponses.internalError(correlationId);
        }

        // FINE, not WARNING. Every one of these is an ordinary client mistake - a wrong password,
        // a stale form, a pet someone else already deleted - and logging them at a level that is
        // on by default turns the server log into a transcript of user errors, which is how the
        // one line that mattered gets missed.
        LOGGER.log(Level.FINE, failure, () -> status + " " + failure.getMessage());

        Response.Status known = Response.Status.fromStatusCode(status);
        String code = known != null ? known.name() : "HTTP_" + status;
        return ErrorResponses.json(status, code, failure.getMessage());
    }
}
