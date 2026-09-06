package com.petlee.rest.mapper;

import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A request body that is not valid JSON is the client's mistake, so it is a <strong>400</strong>.
 *
 * <p>Without this mapper it is a 500: JSON-B raises {@link JsonbException} while the runtime is
 * reading the entity, nothing catches it, and {@link GenericExceptionMapper} would report the
 * server as broken for a body the server was right to reject. The caller then sees "an unexpected
 * error occurred" and has no reason to look at what they sent.
 *
 * <p>The parser's own message is not passed on. It quotes the offending text and its offset, which
 * is an echo of unvalidated input back to the caller, and on a mis-encoded body it can carry
 * fragments of whatever else was in the buffer. The code {@code MALFORMED_JSON} says everything a
 * client can act on; the full message goes to the log at {@code FINE}.
 *
 * <p>This handles the unwrapped case. A parse failure wrapped by the runtime in a
 * {@code ProcessingException} arrives at {@link GenericExceptionMapper}, which searches the cause
 * chain and reuses {@link #MALFORMED_JSON_OUTCOME} so both paths answer identically.
 */
@Provider
public class JsonbParseExceptionMapper implements ExceptionMapper<JsonbException> {

    private static final Logger LOGGER =
            Logger.getLogger(JsonbParseExceptionMapper.class.getName());

    static final String MALFORMED_JSON = "MALFORMED_JSON";

    static final String MALFORMED_JSON_MESSAGE = "The request body is not valid JSON.";

    /** Shared with {@link GenericExceptionMapper} so the wrapped and unwrapped paths agree. */
    static final GenericExceptionMapper.Outcome MALFORMED_JSON_OUTCOME =
            new GenericExceptionMapper.Outcome(Response.Status.BAD_REQUEST.getStatusCode(),
                    MALFORMED_JSON, MALFORMED_JSON_MESSAGE, "malformed request JSON");

    @Override
    public Response toResponse(JsonbException failure) {
        LOGGER.log(Level.FINE, failure, () -> "400 " + MALFORMED_JSON);

        return ErrorResponses.json(MALFORMED_JSON_OUTCOME.status, MALFORMED_JSON,
                MALFORMED_JSON_MESSAGE);
    }
}
