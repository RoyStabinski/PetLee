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

import java.io.InputStream;
import java.util.Optional;

/**
 * Registration, authentication and each member's own profile. Creates no session and reads
 * none — the tiers above decide what to remember, and the profile methods act on the caller id
 * passed in, never on one taken from a request body. Returns entities, which carry a password
 * digest the callers must not expose.
 */
@ApplicationScoped
public class UserService {

    private UserRepository users;
    private ImageStore images;

    /** Self-reference, so the scalar overload reaches {@code @Valid} through the CDI proxy. */
    @Inject
    private UserService self;

    /** For CDI only. */
    protected UserService() {
    }

    @Inject
    public UserService(UserRepository users, ImageStore images) {
        this.users = users;
        this.images = images;
    }

    /**
     * Registers a new account. The password is hashed and the role is always USER, so no caller
     * can self-elevate.
     *
     * @param form the validated registration body
     * @return the created user
     * @throws AppException 400 if the telephone number is given but not a valid Israeli one,
     *                      409 if the username or email address is already registered
     */
    @Transactional
    public User register(@Valid RegisterForm form) {
        String username = form.username().trim();
        String email = form.email().trim();
        String phone = phoneOrNull(form.phone());

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
        user.setPhoneNumber(phone);
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

    /**
     * Loads the caller's own account, for {@code GET /api/users/me}.
     *
     * @param callerUserId the caller's id
     * @return the caller
     * @throws AppException 401 if that account no longer exists
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public User findProfile(Long callerUserId) {
        return users.findById(callerUserId).orElseThrow(UserService::accountGone);
    }

    /**
     * Changes the caller's name, email address, telephone number and region. The username
     * cannot change; {@link ProfileForm} has no field for it.
     *
     * <p>Optimistic locking in the same two layers as {@code PetService.update}: the explicit
     * check catches a form opened before another save, {@code @Version} the narrower race of two
     * saves that both pass it. The photo and the password are changed without moving the
     * version, so neither makes an open form stale.
     *
     * @param callerUserId    the caller's id
     * @param form            the validated form
     * @param expectedVersion the version the caller saw when it loaded the profile
     * @return the updated user
     * @throws AppException 400 for an invalid telephone number, 401 if the account no longer
     *                      exists, 409 if the profile changed since it was loaded or the email
     *                      address belongs to another account
     */
    @Transactional
    public User updateProfile(Long callerUserId, @Valid ProfileForm form, Long expectedVersion) {
        String email = form.email().trim();
        String phone = phoneOrNull(form.phone());

        User user = users.findById(callerUserId).orElseThrow(UserService::accountGone);

        // Boxed Long values are compared with equals, never with ==, which tests identity.
        if (expectedVersion == null || !expectedVersion.equals(user.getVersion())) {
            throw concurrentEdit(null);
        }

        // For the message, not the guarantee, exactly as in register.
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
     * Changes the caller's password. Revoking the caller's other credentials is the REST tier's
     * job, since it is the one that holds them.
     *
     * @param callerUserId the caller's id
     * @param form         the validated form
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

    /**
     * Replaces the caller's profile photo, deleting the old file. Same formats and limit as a
     * pet's photographs, since both go through {@link ImageStore}.
     *
     * @param callerUserId the caller's id
     * @param content      the photo's bytes; read, not closed
     * @param contentType  its media type, such as {@code image/png}
     * @return the user with the new photo
     * @throws AppException 400 if the photo is missing, too large or not a supported image type,
     *                      401 if the account no longer exists
     */
    @Transactional
    public User setAvatar(Long callerUserId, InputStream content, String contentType) {
        User user = users.lockById(callerUserId).orElseThrow(UserService::accountGone);
        String previous = user.getAvatarUrl();

        String url = images.store(content, contentType);
        try {
            users.updateAvatarUrl(user, url);
        } catch (RuntimeException notSaved) {
            images.delete(url);
            throw notSaved;
        }

        if (previous != null) {
            images.delete(previous);
        }
        return user;
    }

    /**
     * Removes the caller's profile photo and its file. Removing a photo that is not there is
     * not an error.
     *
     * @param callerUserId the caller's id
     * @return the user, now without a photo
     * @throws AppException 401 if the account no longer exists
     */
    @Transactional
    public User removeAvatar(Long callerUserId) {
        User user = users.lockById(callerUserId).orElseThrow(UserService::accountGone);
        String previous = user.getAvatarUrl();
        if (previous == null) {
            return user;
        }

        users.updateAvatarUrl(user, null);
        images.delete(previous);
        return user;
    }

    /** The one 409 for a profile save that lost a race with another save of the same profile. */
    private static AppException concurrentEdit(Throwable cause) {
        return new AppException(409, AppException.STALE_VERSION,
                "Your profile was changed in another tab. Reload and try again", cause);
    }

    /** For a valid token whose account has since been deleted. */
    private static AppException accountGone() {
        return new AppException(401, "Your account is no longer available; please log in again");
    }

    /**
     * The telephone number as stored: absent stays absent, anything else must be a valid
     * Israeli number and is normalised.
     *
     * @throws AppException 400 if a non-blank value is not a valid Israeli number
     */
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

    /** Trims, treating an all-whitespace value as absent. */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
