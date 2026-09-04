package com.petlee.mapper;

import com.petlee.dto.UserDTO;
import com.petlee.model.User;

/** {@link User} to {@link UserDTO}. One direction only; forms become entities in T-13. */
public final class UserMapper {

    private UserMapper() {
    }

    /**
     * @param user the user, may be {@code null}
     * @return the DTO, or {@code null} for a {@code null} argument. The password is not copied,
     *         because {@link UserDTO} has nowhere to put it.
     */
    public static UserDTO toDto(User user) {
        if (user == null) {
            return null;
        }
        UserDTO dto = new UserDTO();
        dto.setId(user.getUserId());
        dto.setUsername(user.getUserName());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhoneNumber());
        dto.setRole(user.getRole() == null ? null : user.getRole().name());
        return dto;
    }
}
