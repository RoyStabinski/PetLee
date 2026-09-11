package com.petlee.rest.security;

import com.petlee.model.User;

import java.io.Serializable;

/**
 * What "logged in" means, in full: the four facts the REST tier keeps about the caller between
 * requests. {@link CurrentUser#establish} stores one of these in the {@code HttpSession} at
 * login; {@link CurrentUser#from} reads it back; {@link SecurityFilter} decides on it.
 *
 * <h2>It holds no password material</h2>
 * Not the plaintext, not the digest, not the salt. A session's contents are written to disk when a
 * server passivates or replicates it, so anything stored here outlives the request in a file
 * nobody is watching. There is also nothing to gain: authentication already happened, and no later
 * request re-checks a password.
 *
 * <h2>It is a snapshot, not a view of the database</h2>
 * A role changed in the database does not change an already-established session. That is a
 * deliberate trade: re-reading the user on every request would put a database round trip on the
 * hot path to defend against a case — an administrator demoted mid-session — that this system does
 * not have. A demotion takes effect at the user's next login.
 *
 * <h2>A record, not a Facelets-bound type</h2>
 * This never reaches a view: the Faces tier reads {@code userBean.loggedIn} and
 * {@code userBean.admin}, ordinary boolean getters on a plain bean, never this type directly. So
 * the record's implicit accessors ({@code admin()} rather than {@code isAdmin()}) never collide
 * with EL's {@code Introspector}-based resolution the way an entity's would.
 *
 * <p>{@link Serializable} because a session may be passivated or replicated, and a
 * non-serialisable attribute fails that at runtime, on the server, under load — never in a test.
 */
public record SessionUser(Long userId, String username, String fullName, boolean admin)
        implements Serializable {

    /**
     * Builds a session payload from the entity {@code UserService} returns at login.
     *
     * @param u the authenticated user
     * @return the payload to store in the session
     */
    public static SessionUser of(User u) {
        return new SessionUser(u.getUserId(), u.getUserName(), u.getFullName(),
                u.getRole() == User.Role.ADMIN);
    }
}
