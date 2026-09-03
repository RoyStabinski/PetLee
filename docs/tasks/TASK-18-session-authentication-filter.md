# T-18 · Session authentication filter and current-user resolution

| Field | Value |
|---|---|
| **Phase** | 5 — REST tier |
| **Depends on** | T-13, T-17 |
| **Blocks** | T-20, T-22, T-23, T-34 |
| **Estimate** | 5h |

## Goal
Enforce specification §4 "Endpoint Security" — *"Web services will enforce session-based user
authentication on every HTTP request that modifies data"* — in one reusable place.

## Scope — files to create / modify
- `src/main/java/com/petlee/rest/security/Secured.java` (annotation)
- `src/main/java/com/petlee/rest/security/AdminOnly.java` (annotation)
- `src/main/java/com/petlee/rest/security/AuthenticationFilter.java`
- `src/main/java/com/petlee/rest/security/SessionUser.java` (immutable session payload)
- `src/main/java/com/petlee/rest/security/CurrentUser.java` (accessor helper)

## Requirements
1. `@Secured` is a `@NameBinding` annotation usable on a resource class or method.
   `AuthenticationFilter` is a `@Provider @Secured` `ContainerRequestFilter` at
   `@Priority(Priorities.AUTHENTICATION)`.
2. The filter reads the existing `HttpSession` (injected `@Context HttpServletRequest`, then
   `getSession(false)` — **never `getSession(true)`**, which would create a session for every
   anonymous request and defeat the check entirely). It looks for attribute `petlee.user`.
3. Absent session or absent attribute → abort with **401** and an `ErrorDTO` body, code
   `NOT_AUTHENTICATED`. `api-contract.md`: *"Endpoints marked 'auth' reject with 401 if not
   logged in."*
4. `SessionUser` is an immutable record-like type carrying `userId`, `username`, `fullName` and
   `role`. It is what T-20 stores at login. It holds **no password material**.
5. `@AdminOnly` implies `@Secured` and additionally requires `role == ADMIN`, aborting with
   **403** and code `NOT_ADMIN`. Used only by T-34.
6. `CurrentUser` exposes `static Optional<SessionUser> from(HttpServletRequest)` and
   `static Long userIdOrNull(HttpServletRequest)`. Endpoints that are **open but
   session-sensitive** — `GET /api/pets/{id}`, marked `open*` in the contract — use
   `userIdOrNull` to decide whether owner contact details are included. They must not carry
   `@Secured`, because a guest is allowed through; only the *content* changes.
7. Session fixation: T-20 must invalidate and recreate the session on successful login. Provide a
   `static void establish(HttpServletRequest, SessionUser)` helper here that does
   `invalidate()` → `getSession(true)` → `setAttribute`, so T-20 cannot get it wrong.
8. The filter must not query the database. It reads the session attribute placed there at login.
   A per-request user lookup would put a database round trip on the hot path for no benefit.
9. Never log session identifiers or the full `SessionUser` at `INFO`. Log the *username* on 401 at
   `FINE` only.

## Out of scope
- No login/logout endpoints (T-20).
- No JSF page guarding (T-33) — that is the presentation tier's own concern.
- No "remember me", tokens, or JWT. The specification asks for session-based auth.

## Acceptance criteria
1. `POST /api/pets` with no session returns **401** with `ErrorDTO` code `NOT_AUTHENTICATED` and
   no stack trace.
2. The same call after logging in returns 2xx.
3. `GET /api/pets` (open) works with no session and returns 200 — proving the filter binds only to
   annotated endpoints.
4. `GET /api/pets/{id}` with no session returns 200 with null owner contact fields; with a session,
   the fields are populated.
5. An `@AdminOnly` endpoint called by a `USER` returns **403** with code `NOT_ADMIN`; by an `ADMIN`,
   2xx.
6. A request with a `JSESSIONID` cookie for an expired session returns 401, not 500.
7. After `establish`, the `JSESSIONID` value differs from the pre-login value (session fixation
   defence). Assert on the two `Set-Cookie` values.
8. No anonymous request creates a session — check that a plain `GET /api/pets` returns no
   `Set-Cookie`.

## Definition of Done
- [ ] All eight acceptance criteria demonstrated, 7 and 8 as automated tests.
- [ ] `grep -n "getSession(true)"` appears only inside `establish`.
- [ ] Javadoc on `@Secured` states plainly: every POST/PUT/DELETE resource method must carry it or
      `@AdminOnly`, with no exceptions.
