package com.petlee.service;

import com.petlee.model.User;
import com.petlee.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * An in-memory {@link UserRepository} for the service unit tests.
 *
 * <p>Hand-written rather than mocked: ADR-003 closes the dependency list, so there is no Mockito.
 * Subclassing works because every repository method is public and non-final, and the inherited
 * {@code EntityManager} is never touched — each method used here is overridden.
 *
 * <p>Uniqueness mirrors T-06 and the schema: username compared exactly, email compared
 * case-insensitively ({@code ux_users_email_lower}).
 */
class FakeUserRepository extends UserRepository {

    /** Every user "stored", in insertion order. */
    final List<User> saved = new ArrayList<>();

    /** When set, the next {@link #save(User)} throws it — the concurrent-registration race. */
    RuntimeException failNextSaveWith;

    private long nextId = 1L;

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return saved.stream().filter(u -> username.equals(u.getUserName())).findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return saved.stream().filter(u -> equalsIgnoringCase(u.getEmail(), email)).findFirst();
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public Optional<User> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return saved.stream().filter(u -> id.equals(u.getUserId())).findFirst();
    }

    @Override
    public User save(User entity) {
        if (failNextSaveWith != null) {
            RuntimeException failure = failNextSaveWith;
            failNextSaveWith = null;
            throw failure;
        }
        if (entity.getUserId() == null) {
            entity.setUserId(nextId++);
            saved.add(entity);
        }
        return entity;
    }

    /** The most recently saved entity — the tests inspect the digest that reached it. */
    User last() {
        return saved.get(saved.size() - 1);
    }

    private static boolean equalsIgnoringCase(String a, String b) {
        return a != null && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }
}
