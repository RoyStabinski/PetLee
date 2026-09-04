package com.petlee.dto;

/** The body of {@code POST /api/auth/login}: {@code username}, {@code password}. */
public class LoginForm {

    private String username;
    private String password;

    public LoginForm() {
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
}
