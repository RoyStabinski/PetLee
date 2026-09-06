package com.petlee.exception;

/**
 * The request is well formed and permitted, but it clashes with the state the server already holds.
 *
 * <p><strong>Maps to HTTP 409.</strong> Two contract lines require it:
 * {@code POST /api/users/register} — <em>"409 if username/email already exists"</em> — and
 * {@code PUT /api/pets/{id}} — <em>"409 if a concurrent edit happened (optimistic lock)"</em>.
 *
 * <p>The second is specification §4's concurrency-control requirement made visible: T-15 catches
 * the {@code OptimisticLockException} raised by {@code @Version} and rethrows it as this type with
 * code {@code STALE_PET}, so the client learns its copy is out of date instead of silently
 * overwriting someone else's edit.
 *
 * <p>Default code: {@code CONFLICT}. Callers pass a narrower one — {@code USERNAME_TAKEN},
 * {@code EMAIL_TAKEN}, {@code STALE_PET}.
 */
public class ConflictException extends PetLeeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }

    public ConflictException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
