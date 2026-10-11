package com.petlee.repository;

import com.petlee.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@ApplicationScoped
public class UserRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    public Optional<User> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(em.find(User.class, id));
    }

    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return em.createQuery("SELECT u FROM User u WHERE u.userName = :name", User.class)
                .setParameter("name", username)
                .getResultStream().findFirst();
    }

    // LOWER() on both sides, matching ux_users_email_lower: one mailbox, one account,
    // whatever case it is typed in.
    public boolean existsByEmail(String email) {
        return email != null && em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = LOWER(:email)", Long.class)
                .setParameter("email", email).getSingleResult() > 0;
    }

    // The same check for a profile update: the member's own row does not count against them.
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
     * must not make an open profile form stale. The entity is refreshed afterwards, since a bulk
     * update bypasses the persistence context.
     *
     * @param user a managed user
     * @param url  the new URL, or null to remove the photo
     */
    public void updateAvatarUrl(User user, String url) {
        em.createQuery("UPDATE User u SET u.avatarUrl = :url WHERE u.userId = :id")
                .setParameter("url", url).setParameter("id", user.getUserId())
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

    public boolean existsByUsername(String username) {
        return username != null && em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.userName = :name", Long.class)
                .setParameter("name", username).getSingleResult() > 0;
    }

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
