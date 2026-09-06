package com.petlee.exception;

/**
 * The thing addressed by the request does not exist — an unknown pet, user, category or image id.
 *
 * <p><strong>Maps to HTTP 404.</strong> {@code api-contract.md} addresses resources by id
 * throughout ({@code GET /api/pets/{id}}, {@code PUT /api/pets/{id}},
 * {@code DELETE /api/pets/{id}}), and an id that names no row is the ordinary "not found" case,
 * not a server error.
 *
 * <p>T-15 also throws it for an unknown {@code categoryId} on create, so specification §5's
 * "Every posted pet must belong to a predefined category" surfaces as a 404 the caller can read
 * rather than a foreign-key violation surfacing as a 500.
 *
 * <p>Default code: {@code NOT_FOUND}. Callers usually pass something narrower, such as
 * {@code CATEGORY_NOT_FOUND} or {@code PET_NOT_FOUND}.
 */
public class NotFoundException extends PetLeeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String code, String message) {
        super(code, message);
    }

    public NotFoundException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
