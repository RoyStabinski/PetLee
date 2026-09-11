package com.petlee.repository;

import com.petlee.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
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
