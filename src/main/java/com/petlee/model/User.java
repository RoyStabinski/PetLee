package com.petlee.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;


@Entity
@Table(name = "users")
public class User {

    public enum Role {
        USER, ADMIN
    }

    // Long, not long: an unpersisted User must report a null id, not "saved with id 0".
    // ADR-002 #8.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @NotBlank
    @Size(max = 20)
    @Column(name = "user_name", nullable = false, unique = true, length = 20)
    private String userName;

    // The field holds a PBKDF2 digest (T-10), never a password; the column name says so.
    // ADR-002 #7.
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @NotBlank
    @Size(max = 50)
    @Column(name = "full_name", nullable = false, length = 50)
    private String fullName;

    // unique = true is documentation only (schema generation is off). The real constraint is
    // ux_users_email_lower, a unique index on LOWER(email): one mailbox, one account, whatever
    // case it is typed in.
    @NotBlank
    @Email
    @Size(max = 100)
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    // 20 to match schema.sql: the contract's example "050-1234567" is 11 characters. ADR-002 #9.
    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // POST /api/users/register sends "region" and the contract is frozen, so the entity
    // carries it. Deliberately absent from UserDTO — the contract's response has no such key.
    // ADR-002 #1.
    @Size(max = 100)
    @Column(name = "region", length = 100)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role = Role.USER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "owner")
    private List<Pet> pets;

    // Explicit, so that adding a second constructor later cannot silently remove the
    // no-arg one JPA requires.
    public User() {
    }

    @PrePersist
    protected void onCreated() {
        this.createdAt = LocalDateTime.now();
    }

    public List<Pet> getPets() {
        return pets;
    }

    public void setPets(List<Pet> pets) {
        this.pets = pets;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;

    }
}
