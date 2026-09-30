package com.petlee.web.bean;

import com.petlee.dto.RegisterForm;
import com.petlee.dto.UserDTO;
import com.petlee.web.client.ApiCredentials;
import com.petlee.web.client.ApiException;
import com.petlee.web.client.AuthApi;

import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds the signed-in user for the JSF tier and performs login, registration and logout, all
 * through the REST API. The authority is the bearer token in {@link ApiCredentials}; this bean
 * reads the user from there rather than keeping a second copy.
 */
@Named("userBean")
@SessionScoped
public class UserBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(UserBean.class.getName());

    /** Where the registration form's mismatch message is attached. */
    public static final String CONFIRM_PASSWORD_CLIENT_ID = "registerForm:confirmPassword";

    private static final String HOME = "/index.xhtml?faces-redirect=true";
    private static final String LOGIN = "/login.xhtml?faces-redirect=true";

    /** The code {@code ConstraintViolationExceptionMapper} answers a bean-validation failure with. */
    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /**
     * Not transient: CDI injects a serializable proxy, and transient would leave null behind
     * after the container passivates and restores the session.
     */
    @Inject
    private AuthApi authApi;

    /** The token and the signed-in user. Not transient, for the same reason. */
    @Inject
    private ApiCredentials credentials;

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
     * Signs in through {@code POST /api/auth/login}.
     *
     * <p>That call's session belongs to the server-to-server connection, not to the browser, so
     * this browser's own session id is rotated here: the signed-in state lives in it, and a
     * fixated pre-login id must not carry that over.
     *
     * @return home, or null to stay on the login page
     */
    public String login() {
        try {
            authApi.login(trimmed(username), password);
            rotateSessionId();
            return HOME;

        } catch (ApiException failure) {
            Messages.error(failure.getMessage());
            return null;

        } finally {
            clearPasswords();
        }
    }

    /**
     * Creates an account through {@code POST /api/users/register}. Registration does not sign
     * anybody in.
     *
     * @return the login page, or null to stay on the registration page
     */
    public String register() {
        try {
            if (password == null || !password.equals(confirmPassword)) {
                Messages.error(CONFIRM_PASSWORD_CLIENT_ID, "The two passwords do not match.");
                return null;
            }

            authApi.register(new RegisterForm(trimmed(username), password, trimmed(fullName),
                    trimmed(email), trimmed(phone), trimmed(region)));

            Messages.info("Your account has been created. Please log in.");
            return Messages.keep(LOGIN);

        } catch (ApiException failure) {
            Messages.error(VALIDATION_FAILED.equals(failure.getCode())
                    ? violationText(failure.getMessage())
                    : failure.getMessage());
            return null;

        } finally {
            clearPasswords();
        }
    }

    /**
     * Signs out: revokes the token through {@code POST /api/auth/logout} — {@link AuthApi#logout}
     * clears {@link ApiCredentials} whatever the outcome — then invalidates this browser's
     * session. The local logout happens even if the API call fails — a token that
     * already expired is refused with 401, and the user still expects to be signed out.
     *
     * @return home
     */
    public String logout() {
        try {
            authApi.logout();
        } catch (ApiException failure) {
            LOGGER.log(Level.FINE, () -> "API logout failed (" + failure.getStatus()
                    + "); signing out locally anyway");
        }
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
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

    public boolean isLoggedIn() { return credentials.isLoggedIn(); }

    /**
     * Menu visibility only — not authorisation, which the services do.
     *
     * @return whether the signed-in user's role is ADMIN
     */
    public boolean isAdmin() {
        UserDTO user = getCurrentUser();
        return user != null && "ADMIN".equals(user.role());
    }

    /** @return the user's full name for the navigation bar, or null when signed out */
    public String getDisplayName() {
        UserDTO user = getCurrentUser();
        return user == null ? null : user.fullName();
    }

    /**
     * @return up to two initials from the full name, for the profile's avatar circle: the first
     *         letter of the first and last words, or of the username, or "?" when signed out
     */
    public String getInitials() {
        UserDTO user = getCurrentUser();
        if (user == null) {
            return "?";
        }
        String name = user.fullName() == null || user.fullName().isBlank() ? user.username() : user.fullName();
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] words = name.trim().split("\\s+");
        String initials = words[0].substring(0, 1);
        if (words.length > 1) {
            initials += words[words.length - 1].substring(0, 1);
        }
        return initials.toUpperCase();
    }

    /** @return the signed-in user's id, or null */
    public Long getCurrentUserId() {
        UserDTO user = getCurrentUser();
        return user == null ? null : user.id();
    }

    /** @return the signed-in user, or null. Carries no password material. */
    public UserDTO getCurrentUser() {
        return credentials.isLoggedIn() ? credentials.getUser() : null;
    }

    // ------------------------------------------------------------------------------- internals

    /** Gives this browser's session a new id, keeping its contents; see {@link #login}. */
    private static void rotateSessionId() {
        ExternalContext external = FacesContext.getCurrentInstance().getExternalContext();
        if (external.getSession(false) != null) {
            ((HttpServletRequest) external.getRequest()).changeSessionId();
        }
    }

    /**
     * The mapper writes a violation as {@code "<field> <message>"}; the page shows only the
     * message, since the registration messages already name their field.
     */
    private static String violationText(String message) {
        int space = message.indexOf(' ');
        return space < 0 ? message : message.substring(space + 1);
    }

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
