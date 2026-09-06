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
 * Turns any failure escaping a resource method into a JSON response: the status an
 * {@link AppException} or {@link WebApplicationException} already carries, 400 for a request body
 * JSON-B could not parse, and 500 with no detail for anything else.
 *
 * <p>{@code ConstraintViolationException} is deliberately absent — see
 * {@link ConstraintViolationExceptionMapper} for why a branch here would never run.
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
            return json(400, "MALFORMED_JSON", "The request body is not valid JSON.");
        }
        // Logged here and nowhere else; the body carries no stack trace, class name or SQL.
        LOGGER.log(Level.SEVERE, "Unhandled failure", failure);
        return json(500, "INTERNAL_ERROR", "Something went wrong. Please try again.");
    }

    /** Whether anything in the cause chain is a JSON-B or JSON-P parse failure. */
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
