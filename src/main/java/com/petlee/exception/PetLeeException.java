package com.petlee.exception;

/**
 * The base of every failure the business layer can report.
 *
 * <h2>Why unchecked</h2>
 * None of these conditions is locally recoverable — a service that discovers a duplicate username
 * cannot do anything about it, and neither can its caller. Making them checked would only push a
 * {@code throws} clause through every layer between the service and the {@code ExceptionMapper}
 * that finally turns them into a response.
 *
 * <h2>The two fields</h2>
 * <ul>
 *   <li>{@code message} — safe to show a user. It never names a table, a column, a file path or a
 *       user that exists; T-19 copies it verbatim into {@code ErrorDTO.message}.</li>
 *   <li>{@link #getCode() code} — a short machine token such as {@code USERNAME_TAKEN} or
 *       {@code STALE_PET}, so a client can branch on the reason without parsing prose. It is
 *       never {@code null}: a subclass that does not supply one gets
 *       {@linkplain #defaultCode() a default derived from its class name}.</li>
 * </ul>
 *
 * <h2>Status mapping — T-19 implements exactly this table</h2>
 * <table>
 *   <caption>Exception to HTTP status</caption>
 *   <tr><th>Exception</th><th>HTTP</th><th>Contract reference</th></tr>
 *   <tr><td>{@link ValidationException}</td><td>400</td><td>malformed request body</td></tr>
 *   <tr><td>{@link UnauthorizedException}</td><td>401</td>
 *       <td>"401 if credentials are wrong", "reject with 401 if not logged in"</td></tr>
 *   <tr><td>{@link ForbiddenException}</td><td>403</td>
 *       <td>"403 if not the owner", "403 if not owner and not admin"</td></tr>
 *   <tr><td>{@link NotFoundException}</td><td>404</td><td>unknown pet / user id</td></tr>
 *   <tr><td>{@link ConflictException}</td><td>409</td>
 *       <td>"409 if username/email already exists", "409 if a concurrent edit happened"</td></tr>
 * </table>
 *
 * <h2>No transport imports</h2>
 * This package imports nothing from Jakarta REST, Servlet or JPA, and must not start to. These
 * types are thrown by the service tier and also caught by T-24's {@code ApiClient} in the web
 * tier; a dependency on either transport would make the hierarchy unusable on one side of
 * ADR-001's boundary.
 */
public abstract class PetLeeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Never {@code null} — see {@link #defaultCode()}. */
    private final String code;

    /**
     * @param message the user-facing message
     */
    protected PetLeeException(String message) {
        super(message);
        this.code = defaultCode();
    }

    /**
     * @param code    the machine token; {@code null} or blank falls back to {@link #defaultCode()}
     * @param message the user-facing message
     */
    protected PetLeeException(String code, String message) {
        super(message);
        this.code = normalise(code);
    }

    /**
     * @param code    the machine token; {@code null} or blank falls back to {@link #defaultCode()}
     * @param message the user-facing message
     * @param cause   the underlying failure. It is kept for the server log only — T-19 never puts a
     *                cause, or anything derived from one, into a response body.
     */
    protected PetLeeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = normalise(code);
    }

    /**
     * @return the machine token, never {@code null} and never blank
     */
    public String getCode() {
        return code;
    }

    private String normalise(String candidate) {
        return candidate == null || candidate.isBlank() ? defaultCode() : candidate;
    }

    /**
     * The fallback code for the single-argument constructor: the runtime class name without its
     * {@code Exception} suffix, in upper snake case — {@code NotFoundException} becomes
     * {@code NOT_FOUND}.
     *
     * <p>Derived rather than declared so that a new subclass cannot be added without one, which is
     * how a {@code null} code would otherwise reach a client.
     *
     * @return the derived code
     */
    private String defaultCode() {
        String name = getClass().getSimpleName();
        if (name.endsWith("Exception")) {
            name = name.substring(0, name.length() - "Exception".length());
        }
        if (name.isEmpty()) {
            return "ERROR";
        }

        StringBuilder snake = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (i > 0 && Character.isUpperCase(ch)) {
                snake.append('_');
            }
            snake.append(Character.toUpperCase(ch));
        }
        return snake.toString();
    }
}
