package com.petlee.rest.mapper;

import com.petlee.dto.ErrorDTO;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The two things every mapper in this package needs: a JSON error response, and a correlation id.
 *
 * <p>It is a fourth file where T-19 lists three, for one reason — without it, the response-building
 * line and the {@code Content-Type} would be duplicated in each mapper, and a client that receives
 * an HTML error page from one of them breaks in a way that is hard to attribute. One copy means one
 * place to be right.
 */
final class ErrorResponses {

    /**
     * Excludes I, O, 0 and 1. A correlation id is read off a screen and typed into a support
     * message or a log search, so the characters that are confused for each other are left out.
     */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int ID_LENGTH = 8;

    /** The code every 500 carries, whatever failed. */
    static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";

    private ErrorResponses() {
    }

    /**
     * Builds the error response.
     *
     * <p>The media type is set explicitly rather than left to content negotiation. A Jakarta REST
     * error with no entity type can be rendered as the server's own HTML error page, and every
     * client here — the JSF tier through T-24, and T-39's tests — parses JSON and nothing else.
     *
     * @param status the HTTP status
     * @param code   the machine token for {@link ErrorDTO#getCode()}
     * @param message the user-facing message; never a stack trace, a class name or a SQL fragment
     * @return the response to return from an {@code ExceptionMapper}
     */
    static Response json(int status, String code, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO(code, message))
                .build();
    }

    /**
     * The one 500 body this application produces.
     *
     * <p>The message is fixed apart from the reference. What failed, where, and in which class are
     * facts an attacker uses to choose the next probe — a framework version narrows the exploit
     * list, a class name maps the layout, a SQL fragment describes the schema — and none of them
     * helps the user, who can only retry or report it. The reference is what makes reporting
     * useful, because it names the log entry that does hold all three.
     *
     * @param correlationId from {@link #newCorrelationId()}, already written to the log
     * @return the 500 response
     */
    static Response internalError(String correlationId) {
        return json(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), INTERNAL_ERROR_CODE,
                "An unexpected error occurred. Reference: " + correlationId);
    }

    /**
     * A short token that appears in the log line and in the 500's message, so a user who says
     * "it failed and showed me K7M2PQR4" can be matched to the stack trace that caused it.
     *
     * <p>Eight characters from a 32-letter alphabet is about 40 bits — far short of unique, and it
     * does not need to be: it only has to be unambiguous among the failures in one log file around
     * one moment. {@link ThreadLocalRandom} rather than {@code SecureRandom} for the same reason.
     * Nothing is authorised by it, and it identifies a log entry rather than a user.
     *
     * @return the correlation id
     */
    static String newCorrelationId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder id = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}
