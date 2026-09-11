package com.petlee.web.bean;

import com.petlee.dto.RegisterForm;
import com.petlee.dto.UserDTO;
import com.petlee.exception.PetLeeException;
import com.petlee.rest.security.CurrentUser;
import com.petlee.rest.security.SessionUser;
import com.petlee.service.UserService;

import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;

/**
 * Who is signed in, and the three actions that change that — {@code #{userBean}}.
 *
 * <h2>What it holds, and what it refuses to hold</h2>
 * A {@link UserDTO}, which by construction has no password field. The form-backing password
 * properties exist for the length of one submission and are cleared in a {@code finally} whether
 * the attempt succeeded or not, so a password never sits in a session for the thirty minutes the
 * session lasts.
 *
 * <h2>Where the session actually lives</h2>
 * This bean is {@code @SessionScoped} so the browser keeps its user across requests, but the
 * authority is {@code CurrentUser}'s session attribute — {@link #login()} writes it and
 * {@link #logout()} clears it, so the REST tier's {@code SecurityFilter} sees exactly the same
 * signed-in user this bean does; both tiers share one {@code HttpSession}.
 */
@Named("userBean")
@SessionScoped
public class UserBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Where the registration form's mismatch message is attached, so the message appears next to
     * the field it is about rather than in the global region.
     */
    public static final String CONFIRM_PASSWORD_CLIENT_ID = "registerForm:confirmPassword";

    /** Home, after a successful login or a logout. */
    private static final String HOME = "/index.xhtml?faces-redirect=true";

    /** The login page, after a successful registration — registration does not sign anybody in. */
    private static final String LOGIN = "/login.xhtml?faces-redirect=true";

    /**
     * Not {@code transient}. CDI injects a serializable client proxy for an
     * {@code @ApplicationScoped} bean, so ordinary Java serialization carries it through
     * passivation; marking it {@code transient} would leave {@code null} behind after activation
     * and every call would fail with a {@code NullPointerException} on a session the container had
     * merely moved.
     */
    @Inject
    private UserService userService;

    /** The signed-in user, or {@code null}. The only long-lived state in this bean. */
    private UserDTO currentUser;

    // Form backing. Populated by one submission and, for the passwords, cleared by its finally.
    private String username;
    private transient String password;
    private transient String confirmPassword;
    private String fullName;
    private String email;
    private String phone;
    private String region;

    // ------------------------------------------------------------------------------- the actions

    /**
     * Signs in, establishes the shared session, and goes home — or stays put and says why.
     *
     * @return the outcome to navigate to, or {@code null} to stay on the login page
     */
    public String login() {
        try {
            UserDTO user = userService.authenticate(trimmed(username), password);

            HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance()
                    .getExternalContext().getRequest();
            CurrentUser.establish(request, SessionUser.of(user));

            currentUser = user;
            return destinationAfterLogin();

        } catch (PetLeeException failure) {
            error(failure.getMessage());
            return null;

        } finally {
            clearPasswords();
        }
    }

    /**
     * Creates an account and sends the user to the login page.
     *
     * <p>Registration deliberately does not sign anybody in, so the outcome is the login page with
     * a success message waiting there.
     *
     * @return the outcome to navigate to, or {@code null} to stay on the registration page
     */
    public String register() {
        try {
            if (password == null || !password.equals(confirmPassword)) {
                fieldError(CONFIRM_PASSWORD_CLIENT_ID, "The two passwords do not match.");
                return null;
            }

            RegisterForm form = new RegisterForm();
            form.setUsername(trimmed(username));
            form.setPassword(password);
            form.setFullName(trimmed(fullName));
            form.setEmail(trimmed(email));
            form.setPhone(trimmed(phone));
            form.setRegion(trimmed(region));

            userService.register(form);

            info("Your account has been created. Please log in.");
            return keepingMessages(LOGIN);

        } catch (PetLeeException failure) {
            error(failure.getMessage());
            return null;

        } finally {
            clearPasswords();
        }
    }

    /**
     * Signs out and goes home.
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

    /** @return whether somebody is signed in */
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * <strong>Menu visibility only. This is not authorisation.</strong> Every administrative
     * operation is checked server-side, in the service layer, from the caller id this bean passes
     * in — never from this method.
     *
     * @return whether the signed-in user's role is {@code ADMIN}
     */
    public boolean isAdmin() {
        return currentUser != null && "ADMIN".equals(currentUser.getRole());
    }

    /** @return the user's full name for the navigation bar, or {@code null} when signed out */
    public String getDisplayName() {
        return currentUser == null ? null : currentUser.getFullName();
    }

    /** @return the signed-in user's id, or {@code null} */
    public Long getCurrentUserId() {
        return currentUser == null ? null : currentUser.getId();
    }

    /** @return the signed-in user, or {@code null}. Never carries a password — {@link UserDTO} has none. */
    public UserDTO getCurrentUser() {
        return currentUser;
    }

    // ------------------------------------------------------------------------------- internals

    /**
     * Where to go after signing in: back to whatever the user was trying to reach, or home.
     *
     * @return a Faces outcome, always ending in a redirect
     */
    private String destinationAfterLogin() {
        String requested = FacesContext.getCurrentInstance()
                .getExternalContext()
                .getRequestParameterMap()
                .get("returnUrl");

        return isLocalView(requested) ? requested + "?faces-redirect=true" : HOME;
    }

    /**
     * Whether a {@code returnUrl} names a view inside this application and nowhere else.
     *
     * @param path the requested destination
     * @return whether it is safe to navigate to
     */
    static boolean isLocalView(String path) {
        return path != null
                && path.startsWith("/")
                // "//host" and "/\host" are protocol-relative: a browser reads them as absolute.
                && !path.startsWith("//")
                && !path.startsWith("/\\")
                && path.endsWith(".xhtml")
                // A backslash or a colon is an attempt to smuggle in a scheme or a host.
                && path.indexOf('\\') < 0
                && path.indexOf(':') < 0;
    }

    /**
     * Wipes both password fields. Called from a {@code finally} on every submission, successful or
     * not.
     */
    private void clearPasswords() {
        password = null;
        confirmPassword = null;
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Carries the messages added during this request across a redirect.
     *
     * @param outcome the redirect outcome to return
     * @return {@code outcome}, unchanged
     */
    private static String keepingMessages(String outcome) {
        FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
        return outcome;
    }

    private static void error(String text) {
        add(null, FacesMessage.SEVERITY_ERROR, text);
    }

    private static void info(String text) {
        add(null, FacesMessage.SEVERITY_INFO, text);
    }

    private static void fieldError(String clientId, String text) {
        add(clientId, FacesMessage.SEVERITY_ERROR, text);
    }

    private static void add(String clientId, FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(clientId, new FacesMessage(severity, text, null));
    }

    // ------------------------------------------------------------------------ form properties

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
