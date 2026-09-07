package com.petlee.web.client;

/**
 * What every failed API call becomes: a status, a machine {@code code} and a message written for a
 * human.
 *
 * <h2>Why managed beans should not read {@link #getStatus()}</h2>
 * T-24 requirement 5 says the presentation tier must never inspect raw HTTP status codes. The
 * status is kept because a log line without it is useless, but the branches a bean actually needs
 * are named: {@link #isNotAuthenticated()}, {@link #isForbidden()}, {@link #isNotFound()} and
 * {@link #isConflict()}. A bean that writes {@code e.getStatus() == 404} has quietly learned that
 * its data source speaks HTTP; one that writes {@code e.isNotFound()} has not, and would survive
 * the transport being replaced.
 *
 * <h2>Unchecked, deliberately</h2>
 * A JSF action method's signature is fixed by the EL binding — {@code public String save()} — so a
 * checked exception could not travel out of one without a {@code try/catch} in every bean whether
 * it had anything useful to do or not. Beans that can turn a failure into a {@link
 * jakarta.faces.application.FacesMessage} catch it; the rest let it reach T-33's error page.
 *
 * <h2>{@code message} is safe to render</h2>
 * It is T-19's {@code ErrorDTO.message}, which is written for the end user and never carries a
 * stack trace or a SQL fragment. The one exception is a transport failure, where the API answered
 * nothing at all and this class supplies the wording itself.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * The status the API answered with, or {@link #TRANSPORT_FAILURE} when it never answered.
     */
    public static final int TRANSPORT_FAILURE = 503;

    /** The code used when the API could not be reached at all. */
    public static final String API_UNREACHABLE = "API_UNREACHABLE";

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

    /** The HTTP status. For logging and for the named predicates below — not for bean logic. */
    public int getStatus() {
        return status;
    }

    /** T-19's machine code, such as {@code STALE_PET} or {@code NOT_OWNER}. Never {@code null}. */
    public String getCode() {
        return code;
    }

    /** 401 — nobody is logged in. The caller should send the user to the login page. */
    public boolean isNotAuthenticated() {
        return status == 401;
    }

    /** 403 — somebody is logged in, but not somebody allowed to do this. */
    public boolean isForbidden() {
        return status == 403;
    }

    /** 404 — the pet, image or category is gone. T-30 navigates to the not-found page on this. */
    public boolean isNotFound() {
        return status == 404;
    }

    /**
     * 409 — a uniqueness clash ({@code USERNAME_TAKEN}) or a stale edit ({@code STALE_PET}).
     * T-31 turns the second into "this listing was changed by someone else".
     */
    public boolean isConflict() {
        return status == 409;
    }

    /** 400 — the server rejected the field values. The message names the field. */
    public boolean isValidationFailure() {
        return status == 400;
    }

    @Override
    public String toString() {
        return "ApiException[" + status + " " + code + "]";
    }
}
