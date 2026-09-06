package com.petlee.exception;

/**
 * The request body is malformed: a missing required value, one that is too long, out of range, or
 * not one of the contract's enum strings.
 *
 * <p><strong>Maps to HTTP 400.</strong> The contract does not spell out a 400 per endpoint because
 * it is the default answer to a body the server cannot act on; the alternative is letting a
 * too-long value reach PostgreSQL and become a 500, which tells the caller nothing.
 *
 * <p>Beyond the base class this one carries {@linkplain #getField() the offending field's name},
 * so T-27 and T-31 can attach the message to the right JSF input instead of showing a single
 * form-wide error. The field is optional: a validation failure that is not about one field
 * — "at least one image is required" — leaves it {@code null}.
 *
 * <p>Default code: {@code VALIDATION}.
 */
public class ValidationException extends PetLeeException {

    private static final long serialVersionUID = 1L;

    /** The offending field's name, or {@code null} when the failure is not about one field. */
    private final String field;

    public ValidationException(String message) {
        super(message);
        this.field = null;
    }

    public ValidationException(String code, String message) {
        super(code, message);
        this.field = null;
    }

    public ValidationException(String code, String message, Throwable cause) {
        super(code, message, cause);
        this.field = null;
    }

    /**
     * The constructor the services use.
     *
     * @param field   the offending field's name as the DTO spells it — {@code password},
     *                {@code categoryId} — so the web tier can match it to an input without a
     *                translation table
     * @param code    the machine token; {@code null} or blank falls back to {@code VALIDATION}
     * @param message the user-facing message
     */
    public ValidationException(String field, String code, String message) {
        super(code, message);
        this.field = field;
    }

    /**
     * @return the offending field's name, or {@code null} when the failure names no single field
     */
    public String getField() {
        return field;
    }
}
