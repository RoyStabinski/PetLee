package com.petlee.web.client;

import com.petlee.dto.ErrorDTO;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * A call to the REST API that did not succeed, carrying what the server said: its HTTP status and
 * the {@code code} and {@code message} of its {@link ErrorDTO} body. The message is written for
 * people, so the beans can show it as it is.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    /**
     * Builds the exception from a non-2xx response, reading its {@link ErrorDTO} body. A body that
     * is missing or not JSON — a proxy's HTML error page, say — gives a generic message instead.
     * Does not close the response; the caller owns it.
     *
     * @param response the failed response
     * @return the exception to throw
     */
    static ApiException from(Response response) {
        int status = response.getStatus();
        if (response.hasEntity()
                && MediaType.APPLICATION_JSON_TYPE.isCompatible(response.getMediaType())) {
            try {
                ErrorDTO error = response.readEntity(ErrorDTO.class);
                if (error != null && error.message() != null) {
                    return new ApiException(status, error.code(), error.message());
                }
            } catch (ProcessingException | IllegalStateException unreadable) {
                // fall through to the generic message
            }
        }
        return new ApiException(status, "HTTP_" + status,
                "Something went wrong (HTTP " + status + "). Please try again.");
    }

    /** @return the HTTP status the server answered with */
    public int getStatus() {
        return status;
    }

    /** @return the error code from the body, such as {@code NOT_AUTHENTICATED} */
    public String getCode() {
        return code;
    }
}
