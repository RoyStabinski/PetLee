package com.petlee.dto;

import com.petlee.model.User;

/** A user as the contract returns it. No password field, so none can leak through this type. */
public record UserDTO(Long id, String username, String fullName, String email,
                      String phone, String role) {

    public static UserDTO of(User u) {
        return new UserDTO(u.getUserId(), u.getUserName(), u.getFullName(), u.getEmail(),
                u.getPhoneNumber(), u.getRole().name());
    }
}
