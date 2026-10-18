package com.petlee.repository;

import com.petlee.model.User;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

/** Persistence for {@link User}; transactions come from the calling service. */
@ApplicationScoped
public class UserRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    /** @return the user with that id, or empty */
    public Optional<User> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(em.find(User.class, id));
    }

    /** @return the user with that exact username, or empty */
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return em.createQuery("SELECT u FROM User u WHERE u.userName = :name", User.class)
                .setParameter("name", username)
                .getResultStream().findFirst();
    }

    /** @return true if the username is taken */
    public boolean existsByUsername(String username) {
        return username != null && em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.userName = :name", Long.class)
                .setParameter("name", username).getSingleResult() > 0;
    }

    /** @return true if the email is registered, ignoring case */
    public boolean existsByEmail(String email) {
        return email != null && em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = LOWER(:email)", Long.class)
                .setParameter("email", email).getSingleResult() > 0;
    }

    /** @return true if an account other than {@code userId} has the email, ignoring case */
    public boolean existsByEmailForOtherUser(String email, Long userId) {
        return email != null && em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = LOWER(:email)"
                                + " AND u.userId <> :id", Long.class)
                .setParameter("email", email).setParameter("id", userId)
                .getSingleResult() > 0;
    }

    /**
     * Replaces the password digest with a bulk update, which leaves {@code version} alone: a
     * password change must not make an open profile form stale.
     */
    public void updatePassword(Long userId, String digest) {
        em.createQuery("UPDATE User u SET u.password = :digest WHERE u.userId = :id")
                .setParameter("digest", digest).setParameter("id", userId)
                .executeUpdate();
    }

    /** Inserts the user and flushes, so a unique-index violation surfaces here. */
    public User save(User user) {
        em.persist(user);
        em.flush();
        return user;
    }
}
