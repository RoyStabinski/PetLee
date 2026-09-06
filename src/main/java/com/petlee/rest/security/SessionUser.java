package com.petlee.rest.security;

import com.petlee.model.User;

import java.io.Serializable;

/**
 * What the REST tier keeps about the caller between requests. A snapshot taken at login, holding
 * no password material; a role changed in the database takes effect at the user's next login.
 */
public record SessionUser(Long userId, String username, String fullName, boolean admin)
        implements Serializable {

    /**
     * @param u the authenticated user
     * @return the payload to store in the session
     */
    public static SessionUser of(User u) {
        return new SessionUser(u.getUserId(), u.getUserName(), u.getFullName(),
                u.getRole() == User.Role.ADMIN);
    }
}
