package com.petlee.exception;

/**
 * The caller has not proved who they are: bad credentials, or no session at all.
 *
 * <p><strong>Maps to HTTP 401.</strong> {@code api-contract.md} requires it at
 * {@code POST /api/auth/login} — <em>"401 if credentials are wrong"</em> — and for every endpoint
 * marked "auth": <em>"Endpoints marked 'auth' reject with 401 if not logged in"</em>. T-18's
 * filter throws it for the second case, T-13 for the first.
 *
 * <p><strong>The message must not say which half of the credentials was wrong.</strong> T-13
 * throws one identical instance of this exception for an unknown username and for a wrong
 * password, because a service that distinguishes them is an oracle telling an attacker which
 * usernames exist.
 *
 * <p>Default code: {@code UNAUTHORIZED}.
 */
public class UnauthorizedException extends PetLeeException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String code, String message) {
        super(code, message);
    }

    public UnauthorizedException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
