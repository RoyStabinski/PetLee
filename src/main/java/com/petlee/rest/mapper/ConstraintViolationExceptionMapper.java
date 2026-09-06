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
 * <p>Separate from {@link ErrorMapper} because Jakarta REST picks the mapper whose declared type
 * is nearest the exception: the container's own bean-validation mapper beats
 * {@code ExceptionMapper<Throwable>}, and only this exact generic type out-ranks it.
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
