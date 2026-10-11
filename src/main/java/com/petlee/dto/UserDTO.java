package com.petlee.dto;

import com.petlee.model.User;

import java.io.Serializable;

/**
 * A user as the API returns it. No password field, so none can leak through this type.
 * {@code avatarUrl} is the profile photo under {@code /images/}, or null. {@code version} is what
 * a client sends back with {@code PUT /api/users/me?version=}, so a save based on a stale profile
 * is refused rather than overwriting a newer one.
 */
public record UserDTO(Long id, String username, String fullName, String email,
                      String phone, String role, String region, String avatarUrl, Long version)
        implements Serializable {

    public static UserDTO of(User u) {
        return new UserDTO(u.getUserId(), u.getUserName(), u.getFullName(), u.getEmail(),
                u.getPhoneNumber(), u.getRole().name(), u.getRegion(), u.getAvatarUrl(),
                u.getVersion());
    }
}
