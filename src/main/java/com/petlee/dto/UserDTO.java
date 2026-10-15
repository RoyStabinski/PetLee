package com.petlee.dto;

import com.petlee.model.User;

import java.io.Serializable;

/**
 * A user as the API shows it; never carries the password digest. {@code version} is what a
 * client sends back with {@code PUT /api/users/me?version=}, so a save based on a stale profile
 * is refused rather than overwriting a newer one.
 */
public record UserDTO(Long id, String username, String fullName, String email,
                      String phone, String role, String region, Long version) implements Serializable {

    public static UserDTO of(User u) {
        return new UserDTO(u.getUserId(), u.getUserName(), u.getFullName(), u.getEmail(),
                u.getPhoneNumber(), u.getRole().name(), u.getRegion(), u.getVersion());
    }
}
