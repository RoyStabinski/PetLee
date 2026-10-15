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
 * Holds the signed-in user for the JSF tier and performs login, registration (with the optional
 * profile photo) and logout, all through the REST API. The authority is the bearer token in {@link ApiCredentials}; this bean
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
    private static final String PROFILE = "/profile.xhtml?faces-redirect=true";

    // Not transient: what CDI injects is the client proxy of a normal-scoped bean, which is
    // serializable; javac sees only the declared type. Transient would leave null after
    // the session or view is passivated and restored.
    @SuppressWarnings("serial") @Inject private AuthApi authApi;

    /** For the photo chosen at registration. */

    /** The token and the signed-in user. */
    @Inject private ApiCredentials credentials;

    // Form backing. The passwords and the photo are cleared after each submission.
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
     * Creates an account through {@code POST /api/users/register}, signs the new member in with
     * the credentials just submitted, and uploads the chosen photo if there is one.
     *
     * <p>The account exists once the first call returns, so nothing after it sends the user
     * back to the form: a failed sign-in sends them to log in, and a failed upload is a warning
     * on the profile page, since the photo can be added from Edit profile.
     *
     * @return My profile, the login page, or null to stay on the registration page
     */
    public String register() {
        try {
            if (password == null || !password.equals(confirmPassword)) {
                Messages.error(CONFIRM_PASSWORD_CLIENT_ID, "The two passwords do not match.");
                return null;
            }

            authApi.register(new RegisterForm(trimmed(username), password, trimmed(fullName),
                    trimmed(email), trimmed(phone), trimmed(region)));

            try {
                authApi.login(trimmed(username), password);
            } catch (ApiException loginFailed) {
                LOGGER.log(Level.WARNING, () -> "sign-in after registration failed ("
                        + loginFailed.getStatus() + ")");
                Messages.info("Your account has been created. Please log in.");
                return Messages.keep(LOGIN);
            }
            rotateSessionId();

            Messages.info("Welcome to Pet-Lee!");            return Messages.keep(PROFILE);

        } catch (ApiException failure) {
            // Only registration itself can land here; the calls after it catch their own.
            Messages.error(Messages.text(failure));
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

    /** @return whether this browser session holds a live login */
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

    private void clearPasswords() {
        password = null;
        confirmPassword = null;
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    // ------------------------------------------------------------------------ form properties

    /** @return the username typed into the login or registration form */
    public String getUsername() { return username; }

    /** @param username the username typed into the login or registration form */
    public void setUsername(String username) { this.username = username; }

    /** @return the password; null except during a submission */
    public String getPassword() { return password; }

    /** @param password the password */
    public void setPassword(String password) { this.password = password; }

    /** @return the password repeated on the registration form; null except during a submission */
    public String getConfirmPassword() { return confirmPassword; }

    /** @param confirmPassword the password repeated on the registration form */
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    /** @return the registration form's full name */
    public String getFullName() { return fullName; }

    /** @param fullName the registration form's full name */
    public void setFullName(String fullName) { this.fullName = fullName; }

    /** @return the registration form's email address */
    public String getEmail() { return email; }

    /** @param email the registration form's email address */
    public void setEmail(String email) { this.email = email; }

    /** @return the registration form's telephone number, may be blank */
    public String getPhone() { return phone; }

    /** @param phone the registration form's telephone number, may be blank */
    public void setPhone(String phone) { this.phone = phone; }

    /** @return the registration form's region, may be blank */
    public String getRegion() { return region; }

    /** @param region the registration form's region, may be blank */
    public void setRegion(String region) { this.region = region; }

}
