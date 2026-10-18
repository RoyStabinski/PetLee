package com.petlee.web.client;

import com.petlee.dto.UserDTO;

import jakarta.enterprise.context.SessionScoped;

import java.io.Serializable;

/**
 * What this browser session needs to call the API as its user: the bearer token from
 * {@code POST /api/auth/login}, and the user that login returned. Lives in the browser's
 * {@code HttpSession}, so it goes when that session is invalidated or times out.
 */
@SessionScoped
public class ApiCredentials implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
    private UserDTO user;

    /**
     * Records a successful login.
     *
     * @param token the bearer token
     * @param user  the logged-in user
     */
    public void store(String token, UserDTO user) {
        this.token = token;
        this.user = user;
    }

    /**
     * Replaces the remembered user after a profile change, keeping the token, so the header
     * and the profile page show the new details at once. Ignored when nobody is signed in, or
     * when the user is not the one signed in.
     *
     * @param user the user as the API just returned it
     */
    public void refreshUser(UserDTO user) {
        if (token != null && this.user != null && user != null
                && this.user.id() != null && this.user.id().equals(user.id())) {
            this.user = user;
        }
    }

    /** Forgets the token and the user. */
    public void clear() {
        token = null;
        user = null;
    }

    /** @return whether a login has been recorded and not cleared */
    public boolean isLoggedIn() {
        return token != null && user != null;
    }

    /** @return the bearer token, or null */
    public String getToken() {
        return token;
    }

    /** @return the logged-in user, or null */
    public UserDTO getUser() {
        return user;
    }
}
