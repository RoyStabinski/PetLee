package com.petlee.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;


@Entity
@Table(name = "users")
public class User {

    public enum Role {
        USER, ADMIN
    }

    // Long, not long: an unpersisted User must report a null id so equals() below can tell
    // "not saved yet" from "saved with id 0". ADR-002 #8.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name", nullable = false, unique = true, length = 20)
    private String userName;

    // The field holds a PBKDF2 digest (T-10), never a password; the column name says so.
    // ADR-002 #7.
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @Column(name = "full_name", nullable = false, length = 50)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone_number", length = 10)
    private String phoneNumber;

    // POST /api/users/register sends "region" and the contract is frozen, so the entity
    // carries it. Deliberately absent from UserDTO — the contract's response has no such key.
    // ADR-002 #1.
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

    // Identity comparison on the id alone. Two unpersisted users are never equal, even when
    // every other field matches — they are two distinct rows waiting to be written.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return userId != null && userId.equals(other.userId);
    }

    // Constant, deliberately. A hash derived from the id would change when the provider
    // assigns one on persist, and an entity already inside a HashSet would become unfindable.
    @Override
    public int hashCode() {
        return User.class.hashCode();
    }

    // Never print password: toString() output reaches logs and exception messages.
    @Override
    public String toString() {
        return "User{userId=" + userId + ", userName='" + userName + "'}";
    }
}
