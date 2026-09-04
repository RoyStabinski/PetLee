package com.petlee.dto;

/**
 * The body of {@code POST /api/users/register}. Carries a plaintext password inbound only —
 * T-13 hashes it and it is never stored, logged or echoed back.
 */
public class RegisterForm {

    private String username;
    private String password;
    private String fullName;
    private String email;
    private String phone;
    private String region;

    public RegisterForm() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
