package com.petlee.web.bean;

import com.petlee.dto.ChangePasswordForm;
import com.petlee.dto.ProfileForm;
import com.petlee.dto.UserDTO;
import com.petlee.web.client.ApiException;
import com.petlee.web.client.UserApi;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

/**
 * Backs {@code editProfile.xhtml}: the member's details, profile photo and password, read and
 * written through {@code /api/users/me}. The page has three forms, so saving the details,
 * changing the photo and changing the password never submit or validate one another's fields.
 *
 * <p>Loaded from a view action rather than {@code @PostConstruct}, for the reason
 * {@code PetFormBean} gives.
 */
@Named("profileBean")
@ViewScoped
public class ProfileBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** A redirect, so a refresh cannot repeat the submission. */
    private static final String PROFILE = "/profile.xhtml?faces-redirect=true";

    /** Where the password form's mismatch message is attached. */
    public static final String CONFIRM_PASSWORD_CLIENT_ID = "passwordForm:confirmPassword";

    /** The code the API gives a 409 for a stale version, as opposed to a taken email address. */
    private static final String STALE_VERSION = "STALE_VERSION";

    // Not transient: what CDI injects is the client proxy of a normal-scoped bean, which is
    // serializable; javac sees only the declared type. Transient would leave null after
    // the session or view is passivated and restored.
    @SuppressWarnings("serial") @Inject private UserApi userApi;

    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String region;
    private String avatarUrl;
    private int avatarFocusX = 50;
    private int avatarFocusY = 50;

    /** The focal point the editor submits; applied to avatarFocusX and Y once the API accepts it. */
    private Integer focusX;
    private Integer focusY;

    /** The profile's version when the page loaded it; sent back so a stale save is refused. */
    private Long version;

    /** Whether {@link #load} succeeded, so a failed load does not render an empty form. */
    private boolean loaded;

    private transient Part avatarFile;

    // Cleared by each password submission's finally block.
    private transient String currentPassword;
    private transient String newPassword;
    private transient String confirmPassword;

    /**
     * The view action: fills the form from {@code GET /api/users/me}.
     *
     * @return null to render the page, or the login page if the token has expired
     */
    public String load() {
        try {
            UserDTO me = userApi.me();
            username = me.username();
            fullName = me.fullName();
            email = me.email();
            phone = me.phone();
            region = me.region();
            showAvatar(me);
            version = me.version();
            loaded = true;
        } catch (ApiException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                return Messages.sessionExpired();
            }
            Messages.error(failure.getMessage());
        }
        return null;
    }

    /**
     * Saves the details. The server normalises the telephone number and decides every rule;
     * the page's own validators are only a quicker first answer.
     *
     * @return My profile, or null to stay on the form
     */
    public String save() {
        try {
            userApi.updateProfile(new ProfileForm(trimmed(fullName), trimmed(email),
                    trimmed(phone), trimmed(region)), version);
            Messages.info("Your profile has been saved.");
            return Messages.keep(PROFILE);

        } catch (ApiException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                return Messages.sessionExpired();
            }
            if (failure.getStatus() == HttpServletResponse.SC_CONFLICT
                    && STALE_VERSION.equals(failure.getCode())) {
                Messages.error("Your profile was changed in another tab. Reload and try again");
                return null;
            }
            // A 400 names the field and the rule; a taken email address is a 409 of its own.
            Messages.error(Messages.text(failure));
            return null;
        }
    }

    /**
     * Replaces the profile photo with the chosen file. The details form is not submitted, and a
     * new photo does not move the profile's version, so unsaved details can still be saved.
     *
     * @return null to stay on the page, or the login page if the token has expired
     */
    public String uploadAvatar() {
        try {
            if (avatarFile == null || avatarFile.getSize() <= 0) {
                Messages.error("Choose a photo to upload.");
                return null;
            }
            try (InputStream content = avatarFile.getInputStream()) {
                showAvatar(userApi.uploadAvatar(content, avatarFile.getSubmittedFileName(),
                        avatarFile.getContentType()));
            }
            Messages.info("Your profile photo has been updated.");

        } catch (ApiException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                return Messages.sessionExpired();
            }
            Messages.error(failure.getMessage());

        } catch (IOException unreadable) {
            Messages.error("The photo could not be read.");

        } finally {
            avatarFile = null;
        }
        return null;
    }

    /**
     * Removes the profile photo; the initials circle takes its place.
     *
     * @return null to stay on the page, or the login page if the token has expired
     */
    public String removeAvatar() {
        try {
            showAvatar(userApi.removeAvatar());
            Messages.info("Your profile photo has been removed.");
        } catch (ApiException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                return Messages.sessionExpired();
            }
            Messages.error(failure.getMessage());
        }
        return null;
    }

    /**
     * Saves the focal point chosen in the editor (focus.js), from an ajax submit of the focus
     * form. The API refreshes the session's copy of the account, so the header avatar follows.
     *
     * @return null to stay on the page, or the login page if the token has expired
     */
    public String saveAvatarFocus() {
        try {
            showAvatar(userApi.setAvatarFocus(focusX, focusY));
            Messages.info("The photo's focus has been saved.");
        } catch (ApiException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                return Messages.sessionExpired();
            }
            Messages.error(failure.getMessage());
        }
        return null;
    }

    /** Takes the photo and its focal point from the account the API returned. */
    private void showAvatar(UserDTO user) {
        avatarUrl = user.avatarUrl();
        avatarFocusX = user.avatarFocusX();
        avatarFocusY = user.avatarFocusY();
    }

    /**
     * Changes the password. The confirmation is checked here and never sent; the API checks the
     * current password and signs out every other device, keeping this one signed in.
     *
     * @return null to stay on the page, or the login page if the token has expired
     */
    public String changePassword() {
        try {
            if (newPassword == null || !newPassword.equals(confirmPassword)) {
                Messages.error(CONFIRM_PASSWORD_CLIENT_ID, "The two new passwords do not match.");
                return null;
            }
            userApi.changePassword(new ChangePasswordForm(currentPassword, newPassword));
            Messages.info("Your password has been changed. Any other devices signed in to your "
                    + "account have been signed out.");

        } catch (ApiException failure) {
            if (failure.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                return Messages.sessionExpired();
            }
            Messages.error(Messages.text(failure));

        } finally {
            currentPassword = null;
            newPassword = null;
            confirmPassword = null;
        }
        return null;
    }

    // -------------------------------------------------------------------- what the page reads

    /** @return whether the profile loaded, so the page renders the forms */
    public boolean isLoaded() { return loaded; }

    /** @return the username, shown read-only */
    public String getUsername() { return username; }

    /** @return the profile photo's URL, or null for the initials circle */
    public String getAvatarUrl() { return avatarUrl; }

    /** @return the photo's focal point across, in percent */
    public int getAvatarFocusX() { return avatarFocusX; }

    /** @return the photo's focal point down, in percent */
    public int getAvatarFocusY() { return avatarFocusY; }

    /** @return the inline style that crops the photo around its focal point */
    public String getAvatarStyle() { return FocusBean.styleOf(avatarFocusX, avatarFocusY); }

    /** @return the focal point across the editor submits */
    public Integer getFocusX() { return focusX; }

    /** @param focusX the focal point across the editor submits */
    public void setFocusX(Integer focusX) { this.focusX = focusX; }

    /** @return the focal point down the editor submits */
    public Integer getFocusY() { return focusY; }

    /** @param focusY the focal point down the editor submits */
    public void setFocusY(Integer focusY) { this.focusY = focusY; }

    /** @return whether there is a photo to show and to remove */
    public boolean isHasAvatar() { return avatarUrl != null && !avatarUrl.isBlank(); }

    /** @return the initials circle's letters, from the name as last loaded or saved */
    public String getInitials() {
        return UserBean.initialsOf(fullName == null || fullName.isBlank() ? username : fullName);
    }

    // ------------------------------------------------------------------------------- internals

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    // ------------------------------------------------------------------------ form properties

    /** @return the full name */
    public String getFullName() { return fullName; }

    /** @param fullName the full name */
    public void setFullName(String fullName) { this.fullName = fullName; }

    /** @return the email address */
    public String getEmail() { return email; }

    /** @param email the email address */
    public void setEmail(String email) { this.email = email; }

    /** @return the telephone number, may be blank */
    public String getPhone() { return phone; }

    /** @param phone the telephone number, may be blank */
    public void setPhone(String phone) { this.phone = phone; }

    /** @return the region, may be blank */
    public String getRegion() { return region; }

    /** @param region the region, may be blank */
    public void setRegion(String region) { this.region = region; }

    /** @return the photo chosen in the photo form, or null */
    public Part getAvatarFile() { return avatarFile; }

    /** @param avatarFile the photo chosen in the photo form */
    public void setAvatarFile(Part avatarFile) { this.avatarFile = avatarFile; }

    /** @return the current password; null except during a submission */
    public String getCurrentPassword() { return currentPassword; }

    /** @param currentPassword the current password */
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    /** @return the new password; null except during a submission */
    public String getNewPassword() { return newPassword; }

    /** @param newPassword the new password */
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    /** @return the new password repeated; null except during a submission */
    public String getConfirmPassword() { return confirmPassword; }

    /** @param confirmPassword the new password repeated */
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
