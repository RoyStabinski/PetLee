package com.petlee.service;

import com.petlee.dto.ChangePasswordForm;
import com.petlee.dto.ProfileForm;
import com.petlee.dto.RegisterForm;
import com.petlee.model.User;
import com.petlee.repository.UserRepository;
import com.petlee.util.PasswordHasher;
import com.petlee.util.PhoneNumbers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

import java.util.Optional;

/** Registration, login and each member's own profile. */
@ApplicationScoped
public class UserService {

    private UserRepository users;

    /** For CDI only. */
    protected UserService() {
    }

    @Inject
    public UserService(UserRepository users) {
        this.users = users;
    }

    /**
     * Creates a member account.
     *
     * @param form the validated registration form
     * @return the new user
     * @throws AppException 400 for an invalid phone number, 409 if the username or email is taken
     */
    @Transactional
    public User register(@Valid RegisterForm form) {
        String username = form.username().trim();
        String email = form.email().trim();
        String phone = phoneOrNull(form.phone());

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
        user.setPhoneNumber(phone);
        user.setRegion(trimToNull(form.region()));
        user.setRole(User.Role.USER);
        try {
            return users.save(user);
        } catch (PersistenceException race) {
            // Two registrations passed the checks above together; the unique index decided.
            throw new AppException(409, "That username or email address is already registered", race);
        }
    }

    /**
     * @return the user whose credentials match
     * @throws AppException 401 for an unknown username or a wrong password, with one message for
     *                      both so the answer does not reveal which usernames exist
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

    /** @throws AppException 401 if the account no longer exists */
    @Transactional(Transactional.TxType.SUPPORTS)
    public User findProfile(Long userId) {
        return users.findById(userId).orElseThrow(UserService::accountGone);
    }

    /**
     * Changes the caller's name, email address, telephone number and region; the username
     * cannot change. Optimistic locking as in {@code PetService.update}: the explicit check
     * catches a form opened before another save, {@code @Version} two saves that both pass it.
     *
     * @param callerUserId    the caller's id, never one taken from the request body
     * @param form            the validated form
     * @param expectedVersion the version the caller saw when it loaded the profile
     * @return the updated user
     * @throws AppException 400 for an invalid phone number, 401 if the account no longer
     *                      exists, 409 if the profile changed since it was loaded or the email
     *                      address belongs to another account
     */
    @Transactional
    public User updateProfile(Long callerUserId, @Valid ProfileForm form, Long expectedVersion) {
        String email = form.email().trim();
        String phone = phoneOrNull(form.phone());

        User user = users.findById(callerUserId).orElseThrow(UserService::accountGone);
        if (expectedVersion == null || !expectedVersion.equals(user.getVersion())) {
            throw concurrentEdit(null);
        }
        if (users.existsByEmailForOtherUser(email, callerUserId)) {
            throw new AppException(409, "That email address is already registered");
        }

        user.setFullName(form.fullName().trim());
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setRegion(trimToNull(form.region()));
        try {
            return users.save(user);
        } catch (OptimisticLockException e) {
            // Caught first: it is a PersistenceException too, and means something else.
            throw concurrentEdit(e);
        } catch (PersistenceException race) {
            throw new AppException(409, "That email address is already registered", race);
        }
    }

    /**
     * Changes the caller's password. Signing out the caller's other devices is the REST tier's
     * job, since it holds the tokens.
     *
     * @throws AppException 400 if the current password is wrong, 401 if the account no longer
     *                      exists
     */
    @Transactional
    public void changePassword(Long callerUserId, @Valid ChangePasswordForm form) {
        User user = users.findById(callerUserId).orElseThrow(UserService::accountGone);
        if (!PasswordHasher.verify(form.currentPassword(), user.getPassword())) {
            throw new AppException(400, "Your current password is incorrect");
        }
        users.updatePassword(callerUserId, PasswordHasher.hash(form.newPassword()));
    }

    private static AppException concurrentEdit(Throwable cause) {
        return new AppException(409, AppException.STALE_VERSION,
                "Your profile was changed in another tab. Reload and try again", cause);
    }

    private static AppException accountGone() {
        return new AppException(401, "Your account is no longer available; please log in again");
    }

    private static String phoneOrNull(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        String normalized = PhoneNumbers.normalize(value);
        if (normalized == null) {
            throw new AppException(400, PhoneNumbers.INVALID_MESSAGE);
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
