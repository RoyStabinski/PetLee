package com.petlee.service;

import com.petlee.dto.RegisterForm;
import com.petlee.model.User;
import com.petlee.repository.UserRepository;
import com.petlee.util.PasswordHasher;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

import java.util.Optional;

/**
 * Registration and authentication. Creates no session and reads none — the tiers above decide
 * what to remember. Returns entities, which carry a password digest the callers must not expose.
 */
@ApplicationScoped
public class UserService {

    private UserRepository users;

    /** Self-reference, so the scalar overload reaches {@code @Valid} through the CDI proxy. */
    @Inject
    private UserService self;

    /** For CDI only. */
    protected UserService() {
    }

    @Inject
    public UserService(UserRepository users) {
        this.users = users;
    }

    /**
     * Registers a new account. The password is hashed and the role is always USER, so no caller
     * can self-elevate.
     *
     * @param form the validated registration body
     * @return the created user
     * @throws AppException 409 if the username or email address is already registered
     */
    @Transactional
    public User register(@Valid RegisterForm form) {
        String username = form.username().trim();
        String email = form.email().trim();

        // For the message, not the guarantee: the LOWER() unique indexes decide a race,
        // and the catch below turns their verdict into the same 409.
        if (users.existsByUsername(username)) {
            throw new AppException(409, "That username is already taken");
        }
        if (users.existsByEmail(email)) {
            throw new AppException(409, "That email address is already registered");
        }

        User user = new User();
        user.setUserName(username);
        user.setPassword(PasswordHasher.hash(form.password()));
        user.setFullName(form.fullName().trim());
        user.setEmail(email);
        user.setPhoneNumber(trimToNull(form.phone()));
        user.setRegion(trimToNull(form.region()));
        user.setRole(User.Role.USER);

        try {
            return users.save(user);
        } catch (PersistenceException race) {
            throw new AppException(409, "That username or email address is already registered", race);
        }
    }

    /** Scalar overload for the JSF tier, which cannot bind a record. */
    public User register(String username, String password, String fullName, String email,
                         String phone, String region) {
        return self.register(new RegisterForm(username, password, fullName, email, phone, region));
    }

    /**
     * Checks a username and password. An unknown user and a wrong password fail identically,
     * so this cannot be used to discover which accounts exist.
     *
     * @param username the submitted username
     * @param password the submitted plaintext password
     * @return the authenticated user
     * @throws AppException 401 if the credentials are wrong
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public User authenticate(String username, String password) {
        Optional<User> found = users.findByUsername(username == null ? null : username.trim());

        if (found.isEmpty() || password == null
                || !PasswordHasher.verify(password, found.get().getPassword())) {
            throw new AppException(401, "Username or password is incorrect");
        }

        return found.get();
    }

    /**
     * Looks a user up by id.
     *
     * @param id the user id, may be null
     * @return the user, or empty for a null or unknown id
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<User> findById(Long id) {
        return users.findById(id);
    }

    /** Trims, treating an all-whitespace value as absent. */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
