package com.petlee.rest.mapper;

import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParsingException;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The last mapper standing: everything that is not a {@code PetLeeException} and not a JSON-B
 * parse failure ends here.
 *
 * <h2>Nothing about the failure reaches the client</h2>
 * A stack trace names the framework and its version, the class layout, and often the SQL that
 * failed. That is a map of the application handed to whoever asked for it, and it tells the user
 * nothing they can act on. So the log gets the exception in full at {@code SEVERE}, the client
 * gets a fixed sentence and a reference, and the reference is what connects the two.
 *
 * <h2>Being bound to {@code Throwable} does not mean rewriting everything to 500</h2>
 * Jakarta REST picks the mapper whose type is nearest the thrown exception, so this one runs only
 * where no better mapper exists. Three cases arriving here are not internal errors at all, and
 * each is recognised before the fallback:
 * <ul>
 *   <li>{@link WebApplicationException} — a 404 from routing, a 405 from a wrong method, a 415
 *       from a wrong content type. The status it already carries is the right answer; turning
 *       those into 500s would report the server's own routing as broken. T-19 criterion 6 and
 *       requirement 7.</li>
 *   <li>{@link OptimisticLockException} — the safety net for requirement 8. T-15 converts these
 *       into a {@code ConflictException} where it can, but one escaping from a flush at commit
 *       time, after the service method has returned, has nowhere to be caught. It is still a
 *       concurrent edit and still a 409.</li>
 *   <li>A JSON parse failure that reached here wrapped in something else — see
 *       {@link JsonbParseExceptionMapper}, which handles the unwrapped case.</li>
 * </ul>
 *
 * <h2>The cause chain is searched, not just the exception</h2>
 * Neither of the two recognisable failures usually arrives bare: the persistence provider wraps an
 * optimistic-lock failure in a {@code RollbackException} or a {@code PersistenceException}, and
 * the Jakarta REST runtime wraps a reader failure in a {@code ProcessingException}. Matching only
 * the top type would mean the safety net catches nothing in exactly the situations it exists for.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(GenericExceptionMapper.class.getName());

    /** Guards against a cause chain that loops back on itself; ten links is far beyond real depth. */
    private static final int MAX_CAUSE_DEPTH = 10;

    @Override
    public Response toResponse(Throwable failure) {
        Outcome outcome = classify(failure);

        if (outcome.status >= 500) {
            String correlationId = ErrorResponses.newCorrelationId();
            // The whole exception, with its causes, at SEVERE - this is the only record that the
            // request failed, and the correlation id is what ties it to the user's report.
            LOGGER.log(Level.SEVERE, failure,
                    () -> outcome.status + " [" + correlationId + "] " + outcome.logDetail);
            return ErrorResponses.internalError(correlationId);
        }

        LOGGER.log(Level.FINE, failure, () -> outcome.status + " " + outcome.logDetail);
        return ErrorResponses.json(outcome.status, outcome.code, outcome.message);
    }

    /**
     * Decides the status without building a response, so the decision can be unit tested — the
     * same split T-18's filter makes, and for the same reason: a Jakarta REST runtime is a
     * platform service and ADR-003 keeps one off the test classpath.
     *
     * @param failure the exception that escaped a resource method
     * @return what to answer, and what to write in the log
     */
    static Outcome classify(Throwable failure) {
        if (failure instanceof WebApplicationException webApplicationException) {
            return fromWebApplicationException(webApplicationException);
        }

        if (hasCause(failure, OptimisticLockException.class)) {
            // Deliberately the same code T-15 uses, so a client cannot tell whether the conflict
            // was caught by the service or by this net, and does not have to branch on it.
            return new Outcome(Response.Status.CONFLICT.getStatusCode(), "STALE_PET",
                    "This listing was changed by someone else. Reload it and try again.",
                    "optimistic lock escaped the service layer");
        }

        if (hasCause(failure, JsonbException.class) || hasCause(failure, JsonParsingException.class)) {
            return JsonbParseExceptionMapper.MALFORMED_JSON_OUTCOME;
        }

        return new Outcome(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                ErrorResponses.INTERNAL_ERROR_CODE, null,
                "unhandled " + failure.getClass().getName());
    }

    /**
     * Keeps the status the runtime chose, and replaces the body with JSON.
     *
     * <p>The reason phrase — "Not Found", "Method Not Allowed" — is used as the message rather than
     * the exception's own text, which on some servers carries an implementation-specific error
     * code and on all of them can echo part of the request back.
     */
    private static Outcome fromWebApplicationException(WebApplicationException failure) {
        // A WebApplicationException always carries a response in practice; null is defended
        // against rather than expected, and an exception that cannot say what it wants is an
        // internal error like any other.
        return fromStatus(failure.getResponse() != null
                ? failure.getResponse().getStatus()
                : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    /**
     * Turns a status the runtime already chose into an {@link Outcome}, so that the choice can be
     * asserted without constructing a {@code Response} — which, again, needs a runtime the tests
     * do not have.
     *
     * @param status the status carried by the {@link WebApplicationException}
     * @return the outcome, preserving that status
     */
    static Outcome fromStatus(int status) {
        Response.Status known = Response.Status.fromStatusCode(status);
        String code = known != null ? known.name() : "HTTP_" + status;
        String message = known != null ? known.getReasonPhrase() : "Request failed.";

        return new Outcome(status, code, message,
                "WebApplicationException preserved as " + status);
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /**
     * What to answer. {@code message} is {@code null} for a 500, where the body is
     * {@link ErrorResponses#internalError(String)} and carries a correlation id that only exists
     * once the failure is being logged.
     */
    static final class Outcome {

        final int status;
        final String code;
        final String message;

        /** For the log only. It may name classes; the client's message never does. */
        final String logDetail;

        Outcome(int status, String code, String message, String logDetail) {
            this.status = status;
            this.code = code;
            this.message = message;
            this.logDetail = logDetail;
        }
    }
}
