package com.petlee.exception;

/**
 * The caller is authenticated, and is still not allowed to do this.
 *
 * <p><strong>Maps to HTTP 403.</strong> {@code api-contract.md} requires it twice:
 * {@code PUT /api/pets/{id}} — <em>"403 if not the owner"</em> — and
 * {@code DELETE /api/pets/{id}} — <em>"403 if not owner and not admin"</em>. It also backs
 * specification §5's <em>"Only the original poster of a pet listing can remove it (or an
 * administrator)"</em>.
 *
 * <p>The distinction from {@link UnauthorizedException} is not cosmetic. 401 means "I do not know
 * who you are, log in"; 403 means "I know who you are and the answer is still no". Returning 401
 * for the second would invite a logged-in caller to re-authenticate forever.
 *
 * <p>Default code: {@code FORBIDDEN}. Callers pass a narrower one, such as {@code NOT_OWNER}.
 */
public class ForbiddenException extends PetLeeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String code, String message) {
        super(code, message);
    }

    public ForbiddenException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
