package com.petlee.dto;

import com.petlee.model.User;

import java.io.Serializable;

/** A user as the API shows it; never carries the password digest. */
public record UserDTO(Long id, String username, String fullName, String email,
                      String phone, String role, String region) implements Serializable {

    public static UserDTO of(User u) {
        return new UserDTO(u.getUserId(), u.getUserName(), u.getFullName(), u.getEmail(),
                u.getPhoneNumber(), u.getRole().name(), u.getRegion());
    }
}
