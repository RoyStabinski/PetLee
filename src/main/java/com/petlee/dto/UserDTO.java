package com.petlee.dto;

import com.petlee.model.User;

/**
 * A user as the contract returns it: {@code id}, {@code username}, {@code fullName},
 * {@code email}, {@code phone}, {@code role}.
 *
 * <p>There is no password field and no region field, and that is the point of the class — a
 * password cannot leak through a type that has nowhere to put one.
 */
public record UserDTO(Long id, String username, String fullName, String email,
                      String phone, String role) {

    public static UserDTO of(User u) {
        return new UserDTO(u.getUserId(), u.getUserName(), u.getFullName(), u.getEmail(),
                u.getPhoneNumber(), u.getRole().name());
    }
}
