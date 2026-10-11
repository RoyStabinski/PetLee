package com.petlee.repository;

import com.petlee.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

/** The accounts: lookups, the uniqueness checks behind registration and profile edits, and writes. */
@ApplicationScoped
public class UserRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    /**
     * @param id the user's id, may be null
     * @return the user, or empty for a null or unknown id
     */
    public Optional<User> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(em.find(User.class, id));
    }

    /**
     * @param username the exact username, may be null
     * @return the user, or empty for a null or unknown username
     */
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return em.createQuery("SELECT u FROM User u WHERE u.userName = :name", User.class)
                .setParameter("name", username)
                .getResultStream().findFirst();
    }

    /**
     * Compares with LOWER() on both sides, matching ux_users_email_lower: one mailbox, one
     * account, whatever case it is typed in.
     *
     * @param email the address, may be null
     * @return whether any account uses it; false for null
     */
    public boolean existsByEmail(String email) {
        return email != null && em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = LOWER(:email)", Long.class)
                .setParameter("email", email).getSingleResult() > 0;
    }

    /**
     * The same check for a profile update: the member's own row does not count against them.
     *
     * @param email  the address, may be null
     * @param userId the member saving the profile
     * @return whether another account uses the address; false for null
     */
    public boolean existsByEmailForOtherUser(String email, Long userId) {
        return email != null && em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = LOWER(:email)"
                                + " AND u.userId <> :id", Long.class)
                .setParameter("email", email).setParameter("id", userId)
                .getSingleResult() > 0;
    }

    /**
     * Loads a user and locks the row until the transaction ends, so two photo changes for the
     * same member run one after the other and neither leaves the other's file orphaned.
     *
     * @param id the user's id, may be null
     * @return the locked user, or empty
     */
    public Optional<User> lockById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(User.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    /**
     * Sets the profile photo with a bulk update, which leaves {@code version} alone: a new photo
     * must not make an open profile form stale. The focal point goes back to the centre, since
     * the old one was chosen for another picture. The entity is refreshed afterwards, since a
     * bulk update bypasses the persistence context.
     *
     * @param user a managed user
     * @param url  the new URL, or null to remove the photo
     */
    public void updateAvatarUrl(User user, String url) {
        // A short parameter, not the literal 50, so it matches the attribute's type exactly.
        em.createQuery("UPDATE User u SET u.avatarUrl = :url, u.avatarFocusX = :centre,"
                        + " u.avatarFocusY = :centre WHERE u.userId = :id")
                .setParameter("url", url).setParameter("centre", (short) 50)
                .setParameter("id", user.getUserId())
                .executeUpdate();
        em.refresh(user);
    }

    /**
     * Moves the profile photo's focal point with a bulk update, leaving {@code version} alone
     * for the same reason as {@link #updateAvatarUrl}.
     *
     * @param user a managed user
     * @param x    percent of the width, 0 to 100, already validated
     * @param y    percent of the height, 0 to 100, already validated
     */
    public void updateAvatarFocus(User user, int x, int y) {
        em.createQuery("UPDATE User u SET u.avatarFocusX = :x, u.avatarFocusY = :y WHERE u.userId = :id")
                .setParameter("x", (short) x).setParameter("y", (short) y)
                .setParameter("id", user.getUserId())
                .executeUpdate();
        em.refresh(user);
    }

    /**
     * Replaces the password digest with a bulk update, leaving {@code version} alone for the
     * same reason as {@link #updateAvatarUrl}.
     *
     * @param userId the user's id
     * @param digest the new PBKDF2 digest, never a password
     */
    public void updatePassword(Long userId, String digest) {
        em.createQuery("UPDATE User u SET u.password = :digest WHERE u.userId = :id")
                .setParameter("digest", digest).setParameter("id", userId)
                .executeUpdate();
    }

    /**
     * @param username the exact username, may be null
     * @return whether an account has it; false for null
     */
    public boolean existsByUsername(String username) {
        return username != null && em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.userName = :name", Long.class)
                .setParameter("name", username).getSingleResult() > 0;
    }

    /**
     * Inserts a new user or merges a detached one, and flushes, so a unique-index violation or
     * an optimistic-lock failure surfaces here, where the service turns it into a 409.
     *
     * @param user the user to save
     * @return the managed user
     */
    public User save(User user) {
        if (user.getUserId() == null) {
            em.persist(user);
            em.flush();
            return user;
        }
        User merged = em.merge(user);
        em.flush();
        return merged;
    }
}
