# T-33 · Page-level access control and error pages

| Field | Value |
|---|---|
| **Phase** | 7 — Presentation |
| **Depends on** | T-25, T-26 |
| **Blocks** | T-40 |
| **Estimate** | 4h |

## Goal
Stop guests reaching authenticated pages, and replace container stack traces with pages a user can
understand.

## Scope — files to create / modify
- `src/main/java/com/petlee/web/filter/PageAccessFilter.java`
- `src/main/webapp/error/403.xhtml`, `404.xhtml`, `500.xhtml`, `expired.xhtml`
- `src/main/webapp/WEB-INF/web.xml` (modify — `<error-page>` entries)

## Requirements
1. `PageAccessFilter` is a `@WebFilter` over `*.xhtml` classifying each request:
   - **Public**: `index.xhtml`, `petDetails.xhtml`, `login.xhtml`, `register.xhtml`, everything
     under `/error/` and `/resources/`.
   - **Authenticated**: `addPet.xhtml`, `editPet.xhtml`, `profile.xhtml`.
   - **Admin**: `admin.xhtml` (T-35).
2. Use an **allow-list**: anything not explicitly public requires a login. A deny-list means every
   page added later is public by default until someone remembers to protect it.
3. A guest hitting an authenticated page is redirected to `login.xhtml` with a `returnUrl`
   parameter, and T-26's `login()` honours it after a successful login. Losing the destination
   forces the user to navigate back manually.
4. A non-admin hitting an admin page gets `403.xhtml` — **not** a redirect to login, which would
   wrongly imply that logging in again would help.
5. This filter is convenience and clarity, **not** the security boundary. Every operation is
   already enforced server-side by T-18. Say so in the class Javadoc, so nobody later weakens the
   REST checks believing this filter covers them.
6. `web.xml` `<error-page>` entries map 403, 404, 500 and
   `jakarta.faces.application.ViewExpiredException` to the four pages.
7. `500.xhtml` shows a generic apology and the correlation id from T-19 where available. **No
   stack trace, no exception class, no SQL** — even in `Development` project stage, because the
   stage is a configuration value and could be wrong in production.
8. `expired.xhtml` explains that the session timed out and links to login. This is otherwise a
   frequent and baffling failure after the 30-minute timeout from T-17.
9. All four pages use T-25's template and `#{msg.*}` text, so an error still looks like the
   application.
10. Error responses must carry the correct HTTP status, not 200. A 200 with an error page confuses
    crawlers, monitoring and tests alike.

## Out of scope
- No REST error handling (T-19).
- No role management UI.
- No audit logging.

## Acceptance criteria
1. A guest requesting `/profile.xhtml` lands on login with `returnUrl` set; after logging in they
   arrive at `/profile.xhtml`.
2. A guest requesting `/addPet.xhtml` and `/editPet.xhtml` is likewise redirected.
3. A logged-in `USER` requesting `/admin.xhtml` sees `403.xhtml` with HTTP status 403.
4. An `ADMIN` requesting `/admin.xhtml` is allowed through.
5. `/index.xhtml` and `/petDetails.xhtml?id=…` remain reachable with no session.
6. A URL matching no view returns `404.xhtml` with status 404.
7. A deliberately thrown exception renders `500.xhtml` with status 500 and **no** stack trace in
   the page source.
8. Submitting a form after the session times out shows `expired.xhtml`, not a raw
   `ViewExpiredException`.
9. CSS and images under `/resources/` load on every error page.
10. A newly added page not listed as public is protected by default — add a scratch page and verify.

## Definition of Done
- [ ] All ten acceptance criteria demonstrated, criterion 7 with page source pasted in.
- [ ] The filter uses an allow-list; verify by reading the code.
- [ ] The Javadoc states that this filter is not the security boundary.
