package com.petlee.rest.mapper;

import com.petlee.dto.ErrorDTO;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 400 for a {@link ConstraintViolationException}, naming the first offending field. The message
 * is shown to users as it is, so it names the field alone ({@code "name must not be blank"}),
 * not the whole path through the method and parameter ({@code "create.arg0.name"}).
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
                .map(v -> fieldOf(v) + " " + v.getMessage())
                .orElse("The request is not valid");
        return Response.status(400)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO("VALIDATION_FAILED", message))
                .build();
    }

    /** @return the last node of the violation's property path, such as {@code name} */
    private static String fieldOf(ConstraintViolation<?> violation) {
        String field = null;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getName() != null) {
                field = node.getName();
            }
        }
        return field != null ? field : violation.getPropertyPath().toString();
    }
}
