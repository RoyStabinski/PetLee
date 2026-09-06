package com.petlee.dto;

/**
 * A user as the contract returns it: {@code id}, {@code username}, {@code fullName},
 * {@code email}, {@code phone}, {@code role}.
 *
 * <p>There is no password field and no region field, and that is the point of the class — a
 * password cannot leak through a type that has nowhere to put one.
 */
public class UserDTO {

    private Long id;
    private String username;
    private String fullName;
    private String email;
    // "phone", not the entity's "phoneNumber". JSON-B reads the accessor name.
    private String phone;
    private String role;

    public UserDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
