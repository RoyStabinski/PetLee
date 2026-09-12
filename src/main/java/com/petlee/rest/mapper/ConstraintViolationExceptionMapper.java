package com.petlee.rest.mapper;

import com.petlee.dto.ErrorDTO;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 400 for a {@link ConstraintViolationException}, naming the first offending field.
 *
 * <h2>Why this cannot live in {@link ErrorMapper}</h2>
 * Jakarta REST does not pick an {@code ExceptionMapper} by how many are registered — it picks the
 * one whose declared generic type is the <em>nearest</em> match to the thrown exception's actual
 * class, and a JAX-RS implementation's own built-in mapper for bean-validation failures is a
 * nearer match to {@link ConstraintViolationException} than {@code ExceptionMapper<Throwable>}
 * ever is. With only {@link ErrorMapper} registered, that built-in mapper wins every time and
 * {@code @Valid} failures never reach it — confirmed live: an authenticated
 * {@code POST /api/pets} with an invalid body came back as a bare, unstyled Payara error page
 * naming no field and leaking the server's version banner, not this application's JSON.
 *
 * <p>Declaring a mapper whose generic type is exactly {@code ConstraintViolationException}
 * out-ranks the container's built-in one, so this class — not {@link ErrorMapper} — is what
 * actually answers a {@code @Valid} failure. {@link ErrorMapper} keeps a comment, not code,
 * explaining why its own {@code ConstraintViolationException} branch would be unreachable.
 */
@Provider
public class ConstraintViolationExceptionMapper
        implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException violations) {
        String message = violations.getConstraintViolations().stream().findFirst()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .orElse("The request is not valid");
        return Response.status(400)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO("VALIDATION_FAILED", message))
                .build();
    }
}
