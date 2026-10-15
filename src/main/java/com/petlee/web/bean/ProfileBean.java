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

import java.io.Serializable;

/**
 * Backs {@code editProfile.xhtml}: the member's details and password, read and written through
 * {@code /api/users/me}. The page has two forms, so saving the details and changing the
 * password never submit or validate one another's fields.
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
    // serializable; javac sees only the declared type.
    @SuppressWarnings("serial") @Inject private UserApi userApi;

    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String region;

    /** The profile's version when the page loaded it; sent back so a stale save is refused. */
    private Long version;

    /** Whether {@link #load} succeeded, so a failed load does not render an empty form. */
    private boolean loaded;

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

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    // ------------------------------------------------------------------------ form properties

    /** @return whether the profile loaded, so the page renders the forms */
    public boolean isLoaded() { return loaded; }

    /** @return the username, shown read-only */
    public String getUsername() { return username; }

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
