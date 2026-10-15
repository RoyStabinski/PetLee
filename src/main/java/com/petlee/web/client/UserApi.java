package com.petlee.web.client;

import com.petlee.dto.ChangePasswordForm;
import com.petlee.dto.ProfileForm;
import com.petlee.dto.UserDTO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

/**
 * The caller's own account, under {@code /api/users/me}. Every call that returns the account
 * also hands it to {@link ApiCredentials#refreshUser}, so the header and the profile page show
 * the change on the very next render.
 */
@ApplicationScoped
public class UserApi {

    @Inject
    private ApiClient api;

    @Inject
    private ApiCredentials credentials;

    /**
     * {@code GET /api/users/me}.
     *
     * @return the caller's account, with its current {@code version}
     * @throws ApiException 401 if the caller is not logged in
     */
    public UserDTO me() {
        return remember(api.get("/users/me", UserDTO.class));
    }

    /**
     * {@code PUT /api/users/me?version=}.
     *
     * @param form    the new name, email address, telephone number and region
     * @param version the {@code version} from {@link #me}; null is sent as absent
     * @return the updated account
     * @throws ApiException 400 for an invalid form or telephone number, 401 if not logged in,
     *                      409 if the version is stale or the email address is taken
     */
    public UserDTO updateProfile(ProfileForm form, Long version) {
        // HashMap, not Map.of: the version may be null, and ApiClient skips it then.
        Map<String, Object> query = new HashMap<>();
        query.put("version", version);
        return remember(api.put("/users/me", query, form, UserDTO.class));
    }

    /**
     * {@code PUT /api/users/me/password}. The API revokes the caller's other tokens and keeps
     * this session's, so the user stays signed in here.
     *
     * @param form the current and the new password
     * @throws ApiException 400 if the current password is wrong or the new one too short,
     *                      401 if not logged in
     */
    public void changePassword(ChangePasswordForm form) {
        api.put("/users/me/password", form, Void.class);
    }

    private UserDTO remember(UserDTO user) {
        credentials.refreshUser(user);
        return user;
    }
}
