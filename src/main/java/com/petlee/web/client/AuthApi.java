package com.petlee.web.client;

import com.petlee.dto.LoginForm;
import com.petlee.dto.LoginResponse;
import com.petlee.dto.RegisterForm;
import com.petlee.dto.UserDTO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Login, logout and registration, through {@code /api/auth} and {@code /api/users}. */
@ApplicationScoped
public class AuthApi {

    @Inject
    private ApiClient api;

    @Inject
    private ApiCredentials credentials;

    /**
     * {@code POST /api/auth/login}, keeping the token and the user in {@link ApiCredentials}.
     *
     * @param username the submitted username
     * @param password the submitted password
     * @return the logged-in user
     * @throws ApiException 401 if the credentials are wrong
     */
    public UserDTO login(String username, String password) {
        LoginResponse response = api.post("/auth/login", new LoginForm(username, password),
                LoginResponse.class);
        credentials.store(response.token(), response.user());
        return response.user();
    }

    /**
     * {@code POST /api/auth/logout} to revoke the token, then forgets it. The credentials are
     * cleared even when the call fails — a token that already expired answers 401.
     *
     * @throws ApiException if the server could not revoke the token
     */
    public void logout() {
        try {
            if (credentials.getToken() != null) {
                api.post("/auth/logout", null, Void.class);
            }
        } finally {
            credentials.clear();
        }
    }

    /**
     * {@code POST /api/users/register}. Logs nobody in.
     *
     * @param form the registration
     * @return the created user
     * @throws ApiException 400 for an invalid form, 409 for a taken username or email address
     */
    public UserDTO register(RegisterForm form) {
        return api.post("/users/register", form, UserDTO.class);
    }
}
