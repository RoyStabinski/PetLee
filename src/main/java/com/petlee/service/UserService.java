package com.petlee.service;

import com.petlee.dto.RegisterForm;
import com.petlee.dto.UserDTO;
import com.petlee.exception.ConflictException;
import com.petlee.exception.UnauthorizedException;
import com.petlee.exception.ValidationException;
import com.petlee.mapper.UserMapper;
import com.petlee.model.User;
import com.petlee.repository.UserRepository;
import com.petlee.utilities.PasswordHasher;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Registration and authentication — the rules behind {@code POST /api/users/register} and
 * {@code POST /api/auth/login} (specification §3, §9.3).
 *
 * <h2>What this class does not do</h2>
 * It creates no session and reads none. {@code HttpSession} belongs to T-20, which calls
 * {@link #authenticate(String, String)} and then decides what to remember; keeping it out of here
 * is what lets these rules be tested without a container, and what stops a second caller from
 * quietly depending on a session that a JSF request would not have.
 *
 * <h2>DTOs only</h2>
 * No method returns a {@link User}. That is not tidiness: {@code User} carries the password digest
 * and the region, {@link UserDTO} has nowhere to put either, so a leak through this boundary is a
 * compile error rather than a review finding.
 */
@ApplicationScoped
public class UserService {

    private static final Logger LOGGER = Logger.getLogger(UserService.class.getName());

    /** Letters, digits and underscore, 3–20 characters — the width of {@code user_name}. */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    /**
     * Deliberately permissive: one {@code @}, something either side, a dot in the domain. A
     * stricter pattern rejects addresses that are legal under RFC 5322 and delivers nothing in
     * return — the real proof that a mailbox exists is a confirmation mail, which specification §3
     * does not ask for.
     */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private static final int USERNAME_MAX = 20;
    private static final int PASSWORD_MIN = 8;
    private static final int FULL_NAME_MAX = 50;
    private static final int EMAIL_MAX = 100;
    /** 20, not the task file's 10 — {@code phone_number} was widened by ADR-002 #9. */
    private static final int PHONE_MAX = 20;
    private static final int REGION_MAX = 100;

    /**
     * One message and one code for every authentication failure, whatever caused it. Held as a
     * constant so the two throw sites cannot drift apart — see
     * {@link #authenticate(String, String)}.
     */
    private static final String BAD_CREDENTIALS_CODE = "BAD_CREDENTIALS";
    private static final String BAD_CREDENTIALS_MESSAGE = "Username or password is incorrect";

    private UserRepository users;

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
     * <p>Validation runs before the database is touched, so a bad body costs no query. The
     * password is hashed with {@link PasswordHasher}; the plaintext is never assigned to
     * {@link User#setPassword(String)} and never logged.
     *
     * <p>The role is forced to {@link User.Role#USER}. {@link RegisterForm} has no {@code role}
     * field precisely so a caller cannot self-elevate by posting {@code "role":"ADMIN"}, and this
     * method must not reintroduce one — an admin is made by T-34 or by hand in the database.
     *
     * @param form the registration body; must not be {@code null}
     * @return the created user, without a password field
     * @throws ValidationException <strong>400</strong> — a field is missing, too long, or does not
     *         match its pattern. {@link ValidationException#getField()} names which.
     * @throws ConflictException <strong>409</strong> — {@code api-contract.md}: "409 if
     *         username/email already exists". Thrown by the pre-check, and again by the database
     *         constraint if two registrations race past it.
     */
    @Transactional
    public UserDTO register(RegisterForm form) {
        if (form == null) {
            throw new ValidationException("EMPTY_BODY", "A registration body is required");
        }

        String username = trimToNull(form.getUsername());
        String password = form.getPassword();
        String fullName = trimToNull(form.getFullName());
        String email = trimToNull(form.getEmail());
        String phone = trimToNull(form.getPhone());
        String region = trimToNull(form.getRegion());

        validateForRegistration(username, password, fullName, email, phone, region);

        // The pre-check exists for the message, not for the guarantee. Two simultaneous
        // registrations can both pass it; ux_users_email_lower and the user_name UNIQUE are what
        // actually decide, and the catch below turns their verdict into the same 409.
        if (users.existsByUsername(username)) {
            throw new ConflictException("USERNAME_TAKEN", "That username is already taken");
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("EMAIL_TAKEN", "That email address is already registered");
        }

        User user = new User();
        user.setUserName(username);
        user.setPassword(PasswordHasher.hash(password));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setRegion(region);
        user.setRole(User.Role.USER);

        try {
            User saved = users.save(user);
            LOGGER.log(Level.INFO, () -> "Registered user " + saved.getUserName());
            return UserMapper.toDto(saved);
        } catch (PersistenceException e) {
            // AbstractRepository.save flushes, so a unique-index violation arrives here rather
            // than at commit, where it would already be wrapped as a rollback and be unreadable.
            // The cause is kept for the log only; T-19 puts none of it in the response.
            LOGGER.log(Level.FINE, e, () -> "Registration lost a race for " + username);
            throw new ConflictException("USER_EXISTS",
                    "That username or email address is already registered", e);
        }
    }

    /**
     * Checks a username and password.
     *
     * <p>An unknown username and a wrong password produce the <strong>same</strong> exception,
     * with byte-identical code and message. Telling them apart would make this method an oracle
     * that answers "does this account exist?" to anyone who asks. The verification itself runs
     * through {@link PasswordHasher#verify}, whose comparison is constant-time.
     *
     * @param username the submitted username
     * @param password the submitted plaintext password
     * @return the authenticated user
     * @throws UnauthorizedException <strong>401</strong> — {@code api-contract.md}: "401 if
     *         credentials are wrong"
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public UserDTO authenticate(String username, String password) {
        Optional<User> found = users.findByUsername(trimToNull(username));

        if (found.isEmpty() || password == null
                || !PasswordHasher.verify(password, found.get().getPassword())) {
            // One throw site, so the two cases cannot diverge in message, code or cost.
            LOGGER.log(Level.FINE, () -> "Failed login attempt for " + username);
            throw new UnauthorizedException(BAD_CREDENTIALS_CODE, BAD_CREDENTIALS_MESSAGE);
        }

        return UserMapper.toDto(found.get());
    }

    /**
     * Looks a user up by id.
     *
     * <p>This was written expecting T-18's filter to call it once per request to rehydrate the
     * session user. It does not: T-18 requirement 8 forbids the filter from querying the database,
     * because a per-request lookup puts a round trip on the hot path of every protected call, and
     * what it would detect — a role or account changed mid-session — is not something this system
     * does. The session snapshot is authoritative until the next login. The method stays for the
     * web tier, which needs a user by id for the profile screen (T-32).
     *
     * @param id the user id, may be {@code null}
     * @return the user, or empty for a {@code null} or unknown id; never {@code null}. Absence is
     *         the ordinary answer here rather than a 404 — the caller decides what it means.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<UserDTO> findById(Long id) {
        return users.findById(id).map(UserMapper::toDto);
    }

    private void validateForRegistration(String username, String password, String fullName,
                                         String email, String phone, String region) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new ValidationException("username", "USERNAME_INVALID",
                    "Username must be 3 to " + USERNAME_MAX
                            + " characters, using letters, digits and underscore only");
        }
        if (password == null || password.length() < PASSWORD_MIN) {
            throw new ValidationException("password", "PASSWORD_TOO_SHORT",
                    "Password must be at least " + PASSWORD_MIN + " characters");
        }
        if (fullName == null || fullName.length() > FULL_NAME_MAX) {
            throw new ValidationException("fullName", "FULL_NAME_INVALID",
                    "Full name is required and must be at most " + FULL_NAME_MAX + " characters");
        }
        if (email == null || email.length() > EMAIL_MAX || !EMAIL.matcher(email).matches()) {
            throw new ValidationException("email", "EMAIL_INVALID",
                    "A valid email address of at most " + EMAIL_MAX + " characters is required");
        }
        // Optional, but bounded: the column is VARCHAR(20), and an over-long value would
        // otherwise fail at the INSERT as a 500 the caller cannot act on.
        if (phone != null && phone.length() > PHONE_MAX) {
            throw new ValidationException("phone", "PHONE_TOO_LONG",
                    "Phone number must be at most " + PHONE_MAX + " characters");
        }
        if (region != null && region.length() > REGION_MAX) {
            throw new ValidationException("region", "REGION_TOO_LONG",
                    "Region must be at most " + REGION_MAX + " characters");
        }
    }

    /**
     * Trims, and treats an all-whitespace value as absent. A body carrying {@code "fullName": "  "}
     * is a missing name, not a two-space one.
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
