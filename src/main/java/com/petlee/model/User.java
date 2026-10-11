package com.petlee.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Validation lives on the form records in com.petlee.dto; persistence.xml disables it here.
@Entity
@Table(name = "users")
public class User {

    public enum Role {
        USER, ADMIN
    }

    // Long, not long: an unpersisted User must report a null id, not id 0.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name", nullable = false, unique = true, length = 20)
    private String userName;

    // Holds a PBKDF2 digest, never a password.
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @Column(name = "full_name", nullable = false, length = 50)
    private String fullName;

    // unique = true is documentation only; ux_users_email_lower on LOWER(email) is the constraint.
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "region", length = 100)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role = Role.USER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // A URL under /images/, or null for the initials circle. Changed by a bulk update in
    // UserRepository, so a new photo does not move the version and stale an open profile form.
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    // Optimistic locking for the profile form, as on Pet.
    @Version
    @Column(name = "version")
    private Long version;

    /** For JPA. */
    public User() {
    }

    @PrePersist
    protected void onCreated() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }

    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }

    public void setUserName(String userName) { this.userName = userName; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }

    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }

    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRegion() { return region; }

    public void setRegion(String region) { this.region = region; }

    public Role getRole() { return role; }

    public void setRole(Role role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    // No setter: see the field.
    public String getAvatarUrl() { return avatarUrl; }

    // Long, not long: null until the row is first written. No setter — JPA owns this field.
    public Long getVersion() { return version; }

    public boolean isAdmin() { return role == Role.ADMIN; }
}
