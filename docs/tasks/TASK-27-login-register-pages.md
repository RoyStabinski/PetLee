# T-27 · Login and registration pages

| Field | Value |
|---|---|
| **Phase** | 7 — Presentation |
| **Depends on** | T-25, T-26 |
| **Blocks** | T-40 |
| **Estimate** | 3h |

## Goal
Build the "Registration / Login Page" of specification §12 so users can obtain an account and a
session.

## Scope — files to create / modify
- `src/main/webapp/login.xhtml`
- `src/main/webapp/register.xhtml`

## Requirements
1. Both use T-25's template and fill only the `title` and `content` regions.
2. `login.xhtml`: `<h:form>` with `<h:inputText value="#{userBean.username}">` and
   `<h:inputSecret value="#{userBean.password}">`, a submit button bound to `#{userBean.login}`,
   and a link to `register.xhtml`.
3. `register.xhtml`: inputs for username, password, confirm password, full name, email, phone and
   region, bound to the T-26 properties, submitting to `#{userBean.register}`.
   **`region` must be present** — the frozen contract sends it (ADR-002 #1).
4. Passwords use `<h:inputSecret>` with `redisplay="false"`. The default would re-render the
   password into the HTML source after a failed submit.
5. Client-side validation is limited to `required="true"` and a `<f:validateLength>` on username
   and password matching T-13's rules. It exists to save a round trip; the server remains the
   authority and its messages must always be displayed.
6. Every input has an `<h:outputLabel for="…">` and an adjacent `<h:message for="…">`, so an error
   appears next to the field that caused it rather than only in the global block.
7. All labels, placeholders and buttons come from `#{msg.*}` (T-25).
8. A logged-in user visiting either page is redirected to the home page — landing on a login form
   while already authenticated is a confusing dead end. Implement with a `<f:viewAction>`; T-33
   later generalises this.
9. `phone` input has `maxlength="10"`, matching the column width from T-03 and the validation in
   T-13, so the limit is visible before submission.

## Out of scope
- No password reset (not in specification §3).
- No social or third-party login.
- No CAPTCHA.

## Acceptance criteria
1. Registering through the form creates a user and lands on the login page with a success message.
2. Logging in with those credentials lands on the home page with the navigation showing the user's
   name.
3. A wrong password redisplays the login page with the server's error and an empty password field.
4. Registering a duplicate username shows the 409 message from the server, not a stack trace.
5. Mismatched passwords are caught before any HTTP request is made.
6. Viewing the page source after a failed login shows no password value.
7. An already-logged-in user opening `/login.xhtml` is redirected home.
8. Both pages are usable at 360 px width.
9. Submitting an 11-character phone shows a field-level message.

## Definition of Done
- [ ] All nine acceptance criteria demonstrated, with screenshots for 2, 3 and 8.
- [ ] No hard-coded user-facing text; everything via `#{msg.*}`.
- [ ] Specification §12's "Registration / Login Page" is fully satisfied.
