package com.petlee.web.bean;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;

import java.io.Serializable;

/**
 * <strong>Placeholder.</strong> T-26 replaces every method here with the real thing.
 *
 * <p>T-25 requirement 2 asks for a navigation bar driven by {@code #{userBean.loggedIn}} and
 * {@code #{userBean.admin}}, and says to stub the bean so the shell can be finished and reviewed
 * on its own. This is that stub: the four read-only accessors T-25's template binds to, and
 * nothing else. It holds no state, performs no I/O and reaches no other tier.
 *
 * <p>It is already {@code @SessionScoped} and {@code Serializable} because that is what T-26
 * requires, and changing the scope later would change how every page behaves — better to have the
 * shell reviewed against the scope it will actually run in.
 *
 * <h2>What T-25 can and cannot show</h2>
 * With this stub the guest bar is what renders. The member and administrator bars are exercised by
 * temporarily returning {@code true} from {@link #isLoggedIn()} and {@link #isAdmin()}, which is
 * how T-25 criterion 2 is demonstrated; T-26 makes them answer honestly.
 */
@Named("userBean")
@SessionScoped
public class UserManagedBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** @return {@code false} until T-26 stores a signed-in user */
    public boolean isLoggedIn() {
        return false;
    }

    /**
     * Menu visibility only, and it says so here so that nobody later mistakes it for
     * authorisation. T-18 makes the real decision on the server for every request that matters;
     * a hidden link is not a closed door.
     *
     * @return {@code false} until T-26 knows the user's role
     */
    public boolean isAdmin() {
        return false;
    }

    /** @return {@code null} until T-26 has a user to name */
    public String getDisplayName() {
        return null;
    }

    /** @return {@code null} until T-26 has a user to identify */
    public Long getCurrentUserId() {
        return null;
    }

    /**
     * Bound by the shell's logout button so the template compiles and renders. T-26 makes it call
     * {@code ApiClient.logout} and navigate.
     *
     * @return {@code null} — stay on the current page
     */
    public String logout() {
        return null;
    }
}
