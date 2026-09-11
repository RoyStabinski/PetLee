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
 * Registration and authentication — the rules behind {@code POST /api/users/register} and
 * {@code POST /api/auth/login} (specification §3, §9.3).
 *
 * <h2>What this class does not do</h2>
 * It creates no session and reads none. {@code HttpSession} belongs to the JSF/REST tiers, which
 * call {@link #authenticate(String, String)} and then decide what to remember; keeping it out of
 * here is what lets these rules be exercised without a container, and what stops a second caller
 * from quietly depending on a session that a JSF request would not have.
 *
 * <h2>Where validation happens now</h2>
 * {@link #register(RegisterForm)}'s {@code @Valid} parameter is enforced by the container's Bean
 * Validation provider through a CDI interceptor bound to this {@code @ApplicationScoped} bean: a
 * constraint failure throws {@link jakarta.validation.ConstraintViolationException} before this
 * method body runs. The field-length and pattern checks that used to live here as hand-written
 * code now live as annotations on {@link RegisterForm} instead — one set of rules, checked once,
 * on both the REST and the JSF path (the JSF path through {@link #register(String, String, String,
 * String, String, String)}, which delegates to this method through the injected {@link #self}
 * reference precisely so the container's interceptor is not skipped by a same-instance call).
 *
 * <h2>Returns the entity now</h2>
 * {@link #register(RegisterForm)}, {@link #authenticate(String, String)} and
 * {@link #findById(Long)} return {@link User}. The password digest and the region travel with it;
 * it is the REST resource's job to map to {@code UserDTO} — which has no field for either — before
 * the value reaches the wire, and the JSF tier's job to never render {@code #{user.password}}.
 */
@ApplicationScoped
public class UserService {

    private UserRepository users;

    /**
     * A self-reference, injected rather than reached through {@code this}. CDI's Bean Validation
     * integration enforces {@code @Valid} through an interceptor woven onto the bean's client
     * proxy; a plain {@code this.register(form)} call from {@link #register(String, String,
     * String, String, String, String)} would run on the same instance and bypass that proxy
     * entirely, silently skipping validation for every JSF submission. Calling through
     * {@code self} instead routes the call back through the proxy, so the interceptor — and the
     * validation it enforces — actually runs.
     */
    @Inject
    private UserService self;

    /**
     * For CDI only. An {@code @ApplicationScoped} bean is proxied, and a proxy needs a
     * no-argument constructor it can call.
     */
    protected UserService() {
    }

    @Inject
    public UserService(UserRepository users) {
        this.users = users;
    }

    /**
     * Registers a new account.
     *
     * <p>The password is hashed with {@link PasswordHasher}; the plaintext is never assigned to
     * {@link User#setPassword(String)}.
     *
     * <p>The role is forced to {@link User.Role#USER}. {@link RegisterForm} has no {@code role}
     * field precisely so a caller cannot self-elevate by posting {@code "role":"ADMIN"}, and this
     * method must not reintroduce one — an admin is made by hand in the database.
     *
     * @param form the registration body; validated by the container before this body runs
     * @return the created user, without a password field
     * @throws jakarta.validation.ConstraintViolationException a field is missing, too long, or
     *         does not match its pattern
     * @throws AppException <strong>409</strong> — {@code api-contract.md}: "409 if username/email
     *         already exists". Thrown by the pre-check, and again by the database constraint if
     *         two registrations race past it.
     */
    @Transactional
    public User register(@Valid RegisterForm form) {
        String username = form.username().trim();
        String email = form.email().trim();

        // The pre-check exists for the message, not for the guarantee. Two simultaneous
        // registrations can both pass it; the LOWER() unique indexes are what actually decide,
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
            // The unique-index violation arrives here because save() flushes, rather than at
            // commit, where it would already be wrapped as a rollback and be unreadable.
            throw new AppException(409, "That username or email address is already registered", race);
        }
    }

    /**
     * The JSF tier's entry point into {@link #register(RegisterForm)}.
     *
     * <p>The web tier's package may not import the DTO package at all — a record cannot be
     * bound into a Facelets view, so nothing in that package should have a reason to reach for
     * one. {@code UserBean} holds its fields individually instead and calls this overload, which
     * is the one place a {@link RegisterForm} is assembled from them before delegating — through
     * {@link #self}, not {@code this} — to the canonical, validated method that
     * {@link com.petlee.rest.UserResource} calls directly with the record JSON-B already built.
     *
     * @see #register(RegisterForm)
     */
    public User register(String username, String password, String fullName, String email,
                         String phone, String region) {
        return self.register(new RegisterForm(username, password, fullName, email, phone, region));
    }

    /**
     * Checks a username and password.
     *
     * <p>An unknown username and a wrong password produce the <strong>same</strong> exception,
     * with a byte-identical message. Telling them apart would make this method an oracle that
     * answers "does this account exist?" to anyone who asks. The verification itself runs through
     * {@link PasswordHasher#verify}, whose comparison is constant-time.
     *
     * @param username the submitted username
     * @param password the submitted plaintext password
     * @return the authenticated user
     * @throws AppException <strong>401</strong> — {@code api-contract.md}: "401 if credentials
     *         are wrong"
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public User authenticate(String username, String password) {
        Optional<User> found = users.findByUsername(username == null ? null : username.trim());

        if (found.isEmpty() || password == null
                || !PasswordHasher.verify(password, found.get().getPassword())) {
            // One throw site, so the two cases cannot diverge in message or cost.
            throw new AppException(401, "Username or password is incorrect");
        }

        return found.get();
    }

    /**
     * Looks a user up by id.
     *
     * @param id the user id, may be {@code null}
     * @return the user, or empty for a {@code null} or unknown id; never {@code null}. Absence is
     *         the ordinary answer here rather than a 404 — the caller decides what it means.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<User> findById(Long id) {
        return users.findById(id);
    }

    /**
     * Trims, and treats an all-whitespace value as absent. A body carrying {@code "phone": "  "}
     * is a missing value, not a two-space one.
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
