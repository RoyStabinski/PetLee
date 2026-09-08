package com.petlee.web.bean;

import com.petlee.model.User;
import com.petlee.rest.security.CurrentUser;
import com.petlee.rest.security.SessionUser;
import com.petlee.service.AppException;
import com.petlee.service.UserService;

import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import java.io.Serializable;

/**
 * Holds the signed-in user for the JSF tier and performs login, registration and logout.
 * The authority is the session attribute {@link CurrentUser} writes, which the REST tier reads too.
 */
@Named("userBean")
@SessionScoped
public class UserBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Where the registration form's mismatch message is attached. */
    public static final String CONFIRM_PASSWORD_CLIENT_ID = "registerForm:confirmPassword";

    private static final String HOME = "/index.xhtml?faces-redirect=true";
    private static final String LOGIN = "/login.xhtml?faces-redirect=true";

    /**
     * Not transient: CDI injects a serializable proxy, and transient would leave null behind
     * after the container passivates and restores the session.
     */
    @Inject
    private UserService userService;

    /** The signed-in user, or null. The only long-lived state in this bean. */
    private User currentUser;

    // Form backing. The passwords are cleared by each submission's finally block.
    private String username;
    private transient String password;
    private transient String confirmPassword;
    private String fullName;
    private String email;
    private String phone;
    private String region;

    // ------------------------------------------------------------------------------- the actions

    /**
     * Signs in and establishes the session shared with the REST tier.
     *
     * @return home, or null to stay on the login page
     */
    public String login() {
        try {
            User user = userService.authenticate(trimmed(username), password);

            HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance()
                    .getExternalContext().getRequest();
            CurrentUser.establish(request, SessionUser.of(user));

            currentUser = user;
            return HOME;

        } catch (AppException failure) {
            Messages.error(failure.getMessage());
            return null;

        } finally {
            clearPasswords();
        }
    }

    /**
     * Creates an account. Registration does not sign anybody in.
     *
     * @return the login page, or null to stay on the registration page
     */
    public String register() {
        try {
            if (password == null || !password.equals(confirmPassword)) {
                Messages.error(CONFIRM_PASSWORD_CLIENT_ID, "The two passwords do not match.");
                return null;
            }

            userService.register(trimmed(username), password, trimmed(fullName),
                    trimmed(email), trimmed(phone), trimmed(region));

            Messages.info("Your account has been created. Please log in.");
            return Messages.keep(LOGIN);

        } catch (ConstraintViolationException invalid) {
            Messages.error(Messages.firstViolation(invalid, "That registration could not be accepted."));
            return null;

        } catch (AppException failure) {
            Messages.error(failure.getMessage());
            return null;

        } finally {
            clearPasswords();
        }
    }

    /**
     * Signs out.
     *
     * @return home
     */
    public String logout() {
        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance()
                .getExternalContext().getRequest();
        CurrentUser.terminate(request);
        currentUser = null;
        return HOME;
    }

    /**
     * Sends a signed-in user away from the login and registration pages.
     *
     * @return home
     */
    public String redirectHome() {
        return HOME;
    }

    // -------------------------------------------------------------------- what the shell reads

    public boolean isLoggedIn() { return currentUser != null; }

    /**
     * Menu visibility only — not authorisation, which the services do.
     *
     * @return whether the signed-in user's role is ADMIN
     */
    public boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    /** @return the user's full name for the navigation bar, or null when signed out */
    public String getDisplayName() {
        return currentUser == null ? null : currentUser.getFullName();
    }

    /** @return the signed-in user's id, or null */
    public Long getCurrentUserId() {
        return currentUser == null ? null : currentUser.getUserId();
    }

    /** @return the signed-in user, or null. Carries a password digest — never render it. */
    public User getCurrentUser() { return currentUser; }

    // ------------------------------------------------------------------------------- internals

    private void clearPasswords() {
        password = null;
        confirmPassword = null;
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    // ------------------------------------------------------------------------ form properties

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }

    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getFullName() { return fullName; }

    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }

    public void setPhone(String phone) { this.phone = phone; }

    public String getRegion() { return region; }

    public void setRegion(String region) { this.region = region; }
}
