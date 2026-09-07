package com.petlee.web.bean;

import com.petlee.dto.LoginForm;
import com.petlee.dto.RegisterForm;
import com.petlee.dto.UserDTO;
import com.petlee.web.client.ApiClient;
import com.petlee.web.client.ApiException;

import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Who is signed in, and the three actions that change that — {@code #{userBean}}.
 *
 * <h2>What it holds, and what it refuses to hold</h2>
 * A {@link UserDTO}, which by construction has no password field. The form-backing password
 * properties exist for the length of one submission and are cleared in a {@code finally} whether
 * the attempt succeeded or not, so a password never sits in a session for the thirty minutes the
 * session lasts. That is what T-26 criterion 7 asserts, and the {@code finally} is why it holds
 * even when the server rejects the login.
 *
 * <h2>Everything goes through {@link ApiClient}</h2>
 * ADR-001: no service, no repository, no {@code EntityManager}. This bean cannot even see them.
 * Every failure arrives as an {@link ApiException} carrying the server's own message, which is
 * rendered as a {@link FacesMessage} — the bean never writes its own wording for a rule the server
 * owns, because two copies of a message drift apart the first time either is edited.
 *
 * <h2>Where the session actually lives</h2>
 * In the REST tier. This bean is {@code @SessionScoped} so the browser keeps its user across
 * requests, but the authority is {@code CurrentUser}'s session attribute, set by
 * {@code POST /api/auth/login}. Both tiers share one {@code HttpSession} (ADR-001), which is also
 * why {@link #logout()} does not invalidate anything itself — see the note there.
 */
@Named("userBean")
@SessionScoped
public class UserManagedBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(UserManagedBean.class.getName());

    /**
     * Where the registration form's mismatch message is attached. T-27's registration form must
     * give the confirmation field this client id, or the message falls back to the global region
     * and appears a long way from the field it is about.
     */
    public static final String CONFIRM_PASSWORD_CLIENT_ID = "registerForm:confirmPassword";

    /** Home, after a successful login or a logout. */
    private static final String HOME = "/index.xhtml?faces-redirect=true";

    /** The login page, after a successful registration — registration does not sign anybody in. */
    private static final String LOGIN = "/login.xhtml?faces-redirect=true";

    /**
     * Not {@code transient}. CDI injects a serializable client proxy for an
     * {@code @ApplicationScoped} bean, so it survives passivation; marking it {@code transient}
     * would leave {@code null} behind after activation and every call would fail with a
     * {@code NullPointerException} on a session the container had merely moved.
     */
    @Inject
    private ApiClient api;

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
     * Signs in and goes home — or stays put and says why.
     *
     * <p>The redirect is not decoration. Without it the URL stays on the login page while the home
     * page's markup is rendered, so a refresh re-posts the form and the back button lands on a
     * stale view.
     *
     * <p>T-33 sends a guest here with a {@code returnUrl} so they arrive where they were going;
     * {@link #destinationAfterLogin()} decides whether that parameter can be trusted.
     *
     * @return the outcome to navigate to, or {@code null} to stay on the login page
     */
    public String login() {
        try {
            LoginForm form = new LoginForm();
            form.setUsername(trimmed(username));
            form.setPassword(password);

            currentUser = api.login(form);
            LOGGER.log(Level.FINE, () -> "signed in as " + currentUser.getUsername());
            return destinationAfterLogin();

        } catch (ApiException failure) {
            // The server's own wording, which says the credentials were wrong without saying which
            // half — T-13's decision, and not this bean's to second-guess.
            error(failure.getMessage());
            return null;

        } finally {
            clearPasswords();
        }
    }

    /**
     * Creates an account and sends the user to the login page.
     *
     * <p>Registration deliberately does not sign anybody in (T-20 requirement 3), so the outcome is
     * the login page with a success message waiting there.
     *
     * <p>The only rule checked here is that the two passwords match, because it is the only rule
     * the server cannot check — it never sees the confirmation field. Every other rule is T-13's.
     * A copy here would give the user two sources of truth that drift the first time either moves.
     *
     * @return the outcome to navigate to, or {@code null} to stay on the registration page
     */
    public String register() {
        try {
            if (password == null || !password.equals(confirmPassword)) {
                fieldError(CONFIRM_PASSWORD_CLIENT_ID, message("auth.register.passwordMismatch"));
                return null;
            }

            RegisterForm form = new RegisterForm();
            form.setUsername(trimmed(username));
            form.setPassword(password);
            form.setFullName(trimmed(fullName));
            form.setEmail(trimmed(email));
            form.setPhone(trimmed(phone));
            form.setRegion(trimmed(region));

            UserDTO created = api.register(form);
            LOGGER.log(Level.FINE, () -> "registered " + created.getUsername());

            info(message("auth.register.success"));
            return keepingMessages(LOGIN);

        } catch (ApiException failure) {
            // 409 USERNAME_TAKEN or EMAIL_TAKEN, 400 for a field the server rejected. Its message
            // names the problem in the user's language; this bean adds nothing.
            error(failure.getMessage());
            return null;

        } finally {
            clearPasswords();
        }
    }

    /**
     * Signs out and goes home.
     *
     * <h2>Why this does not invalidate the session itself</h2>
     * It is already invalidated by the time this line runs. Both tiers share one
     * {@code HttpSession} (ADR-001), so {@link ApiClient#logout()} ends it on <em>this</em> request
     * rather than letting the loopback request destroy it from another thread — see the ADR-001
     * amendment, and the {@code IllegalStateException} that made it necessary. Calling
     * {@code ExternalContext.invalidateSession()} here as well would be a second invalidation of a
     * session that has already gone.
     *
     * <p>Clearing {@link #currentUser} is therefore belt and braces: this bean dies with the
     * session. It is done anyway, because a field claiming somebody is signed in when they are not
     * is exactly what a later refactor trips over.
     *
     * @return home
     */
    public String logout() {
        try {
            api.logout();
        } catch (ApiException alreadyGone) {
            // A session that expired underneath the user answers 401. They asked to be logged out
            // and they are logged out; reporting a failure here would be reporting one that is not.
            LOGGER.log(Level.FINE, () -> "logout returned " + alreadyGone.getStatus()
                    + "; the session had already ended");
        } finally {
            currentUser = null;
        }
        return HOME;
    }

    /**
     * Sends a signed-in user away from the login and registration pages.
     *
     * <p>Bound by both as an {@code <f:viewAction if="#{userBean.loggedIn}">}. Landing on a login
     * form while already authenticated is a dead end: the form appears to work, the server answers,
     * and nothing visible changes. T-33 generalises this into a filter over every page; until then
     * these are the only two views where it matters.
     *
     * @return home
     */
    public String redirectHome() {
        return HOME;
    }

    // -------------------------------------------------------------------- what the shell reads

    /** @return whether somebody is signed in, which is what T-25's navigation bar branches on */
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * <strong>Menu visibility only. This is not authorisation.</strong>
     *
     * <p>It decides whether the Admin Panel link is drawn, and nothing else. Every administrative
     * operation is checked server-side by T-18's {@code @AdminOnly} filter, which answers 403 to a
     * member who reaches an admin endpoint by any route at all. A hidden link is not a closed door,
     * and anybody who later weakens a REST check because "the UI does not show it" has misread this
     * method.
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

    /** @return the signed-in user's id, or {@code null}. Used to decide whether a listing is theirs. */
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
     * <p>The parameter is attacker-controlled, so it is never used as given. Without this check
     * {@code login.xhtml?returnUrl=https://example.invalid/} would turn the login page into an open
     * redirect — a convincing phishing link that genuinely starts on this site, and hands the
     * victim to somewhere else the moment they authenticate.
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
     * Wipes both password fields.
     *
     * <p>Called from a {@code finally} on every submission, successful or not. The failure path is
     * the one that matters: a rejected login leaves the user on the page, and without this the
     * password they typed would sit in the session until it expired.
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
     * <p>A {@link FacesMessage} belongs to one {@code FacesContext}, and a redirect starts a new
     * one — so "your account has been created" was added, the browser was sent to the login page,
     * and the message was discarded on the way. The Flash is the scope that spans exactly that gap.
     * Without this call the user is redirected to a login form with no explanation of why they are
     * looking at it.
     *
     * @param outcome the redirect outcome to return
     * @return {@code outcome}, unchanged
     */
    private static String keepingMessages(String outcome) {
        FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
        return outcome;
    }

    /** @return the bundle string for {@code key}, so no wording is written in Java either */
    private static String message(String key) {
        FacesContext context = FacesContext.getCurrentInstance();
        return context.getApplication()
                .evaluateExpressionGet(context, "#{msg['" + key + "']}", String.class);
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
