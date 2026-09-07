package com.petlee.rest.security;

import com.petlee.dto.UserDTO;
import com.petlee.model.User;

import java.io.Serializable;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * What "logged in" means, in full: the four facts the REST tier keeps about the caller between
 * requests. T-20 stores one of these in the {@code HttpSession} at login; {@link CurrentUser}
 * reads it back; {@link AuthenticationFilter} decides on it.
 *
 * <h2>It holds no password material</h2>
 * Not the plaintext, not the digest, not the salt. A session's contents are written to disk when a
 * server passivates or replicates it, so anything stored here outlives the request in a file
 * nobody is watching. There is also nothing to gain: authentication already happened, and no later
 * request re-checks a password.
 *
 * <h2>It is a snapshot, not a view of the database</h2>
 * A role changed in the database does not change an already-established session. That is the
 * deliberate trade named in T-18: re-reading the user on every request would put a database round
 * trip on the hot path to defend against a case — an administrator demoted mid-session — that this
 * system does not have. A demotion takes effect at the user's next login.
 *
 * <h2>Immutable and serialisable</h2>
 * Immutable because two threads can serve two requests on one session at the same time. A
 * {@code record} would say this more briefly, but its implicit accessors ({@code userId()}) would
 * read differently from every other type in this project ({@code getUserId()}), so the fields are
 * final and the accessors conventional.
 * {@link Serializable} because a session may be passivated or replicated, and a non-serialisable
 * attribute fails that at runtime, on the server, under load — never in a test.
 */
public final class SessionUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(SessionUser.class.getName());

    private final Long userId;
    private final String username;
    private final String fullName;
    private final User.Role role;

    /**
     * @param userId   the database id; must not be {@code null} — every authorisation decision
     *                 downstream is made on it
     * @param username the login name, used in log lines
     * @param fullName the display name the JSF tier greets the user with
     * @param role     {@code USER} or {@code ADMIN}; {@code null} is rejected rather than defaulted
     */
    public SessionUser(Long userId, String username, String fullName, User.Role role) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.username = Objects.requireNonNull(username, "username");
        this.fullName = fullName;
        this.role = Objects.requireNonNull(role, "role");
    }

    /**
     * Builds a session payload from the DTO {@code UserService} returns at login.
     *
     * <p>{@link UserDTO#getRole()} is a string, because that is what the contract puts on the
     * wire. An unrecognised value is treated as {@code USER} and logged at {@code WARNING}: the
     * alternative — throwing — turns a data problem into a failed login for a user who did nothing
     * wrong, and the alternative to that — defaulting to {@code ADMIN} — is not an alternative.
     *
     * @param user the authenticated user; must not be {@code null}
     * @return the payload to store in the session
     */
    public static SessionUser of(UserDTO user) {
        Objects.requireNonNull(user, "user");
        return new SessionUser(user.getId(), user.getUsername(), user.getFullName(),
                parseRole(user.getRole(), user.getUsername()));
    }

    private static User.Role parseRole(String role, String username) {
        if (role != null) {
            try {
                return User.Role.valueOf(role.trim().toUpperCase());
            } catch (IllegalArgumentException unknown) {
                // Falls through to the warning below. The exception carries the bad value, which
                // is exactly what must not be trusted, so it is not rethrown.
            }
        }
        LOGGER.log(Level.WARNING, () -> "Unrecognised role '" + role + "' for user " + username
                + "; treating the session as USER");
        return User.Role.USER;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public User.Role getRole() {
        return role;
    }

    /**
     * @return whether this session may reach an {@link AdminOnly} endpoint
     */
    public boolean isAdmin() {
        return role == User.Role.ADMIN;
    }

    /**
     * Names the user and the role, and nothing else. No session id: a log line is the one place a
     * session identifier reliably leaks, and anything holding one can impersonate the user.
     */
    @Override
    public String toString() {
        return "SessionUser[" + username + ", " + role + "]";
    }
}
