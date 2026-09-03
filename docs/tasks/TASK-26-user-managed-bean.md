# T-26 · UserManagedBean — session state and navigation

| Field | Value |
|---|---|
| **Phase** | 7 — Presentation |
| **Depends on** | T-24, T-25 |
| **Blocks** | T-27, T-30, T-32, T-33, T-35 |
| **Estimate** | 4h |

## Goal
Hold the logged-in user for the browser session and drive permission-based navigation
(specification §9.4).

## Scope — files to create / modify
- `src/main/java/com/petlee/web/bean/UserManagedBean.java`

## Requirements
1. `@Named("userBean") @SessionScoped implements Serializable`. `Serializable` is mandatory for a
   session-scoped CDI bean; omitting it fails at deployment or on session passivation.
2. State: a `UserDTO currentUser` field, `null` when logged out. **No password field, ever** —
   not even transiently between the form and the call.
3. Form-backing properties for login (`username`, `password`) and registration (`username`,
   `password`, `confirmPassword`, `fullName`, `email`, `phone`, `region`). Clear all password
   fields in a `finally` block after every submission attempt, successful or not, so they are not
   retained in the session for its whole lifetime.
4. `String login()`:
   - Calls `ApiClient.login`. On success stores the `UserDTO` and returns
     `"/index.xhtml?faces-redirect=true"`. The redirect matters: without it the URL stays on the
     login page and a refresh re-posts the form.
   - On `ApiException` adds a `FacesMessage` with the server's message and returns `null` to stay
     on the page.
5. `String register()`:
   - Checks `password.equals(confirmPassword)` client-side and reports a field-level message if
     not. All other validation is the server's (T-13) — the bean must not duplicate the rules, or
     the two will drift.
   - On success adds a success message and navigates to the login page. Registration does not log
     the user in (T-20 requirement 3).
   - On `ApiException` 409 renders the server's message ("username already taken").
6. `String logout()` — calls `ApiClient.logout`, clears `currentUser`, invalidates the JSF session,
   redirects home. The session must be invalidated **after** the API call, or the cookie needed to
   authenticate the logout call is already gone.
7. Read-only accessors used by T-25's navigation: `boolean isLoggedIn()`,
   `boolean isAdmin()` (`role == ADMIN`), `String getDisplayName()`, `Long getCurrentUserId()`.
8. `isAdmin()` controls **menu visibility only**. It is a convenience, never a security boundary —
   T-18 enforces the real check server-side. State this in the method's Javadoc so nobody later
   mistakes it for authorisation.
9. All server communication goes through `ApiClient`. No service or repository imports (ADR-001).

## Out of scope
- No pet operations (T-28, T-31, T-32).
- No page-access enforcement (T-33).
- No XHTML (T-27).

## Acceptance criteria
1. Logging in with valid credentials sets `loggedIn` true and redirects to the home page.
2. Logging in with a wrong password leaves `loggedIn` false and shows the server's message on the
   login page.
3. After login, `displayName` returns the user's `fullName`.
4. `logout()` clears state; a subsequent protected API call through `ApiClient` returns 401.
5. Registering with mismatched passwords shows a field message and issues **no** HTTP call —
   verify by checking the access log.
6. Registering a duplicate username shows the server's 409 message.
7. After any login attempt, reflection over the bean shows the password field is null or empty.
8. `isAdmin()` is true only for an `ADMIN` user.
9. The bean survives a session passivation/activation cycle (`Serializable` check).

## Definition of Done
- [ ] All nine acceptance criteria demonstrated.
- [ ] No password is stored beyond the duration of a single request.
- [ ] `grep -rE "import com.petlee.(service|repository)"` on this file returns nothing.
