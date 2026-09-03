# T-20 · AuthResource and UserResource

| Field | Value |
|---|---|
| **Phase** | 5 — REST tier |
| **Depends on** | T-13, T-18, T-19 |
| **Blocks** | T-24, T-26 |
| **Estimate** | 4h |

## Goal
Expose registration, login and logout exactly as `api-contract.md` specifies (specification §3).

## Scope — files to create / modify
- `src/main/java/com/petlee/rest/UserResource.java`
- `src/main/java/com/petlee/rest/AuthResource.java`

## Requirements
1. `UserResource` at `@Path("/users")`:
   - `POST /api/users/register` — **open**, consumes and produces JSON, body `RegisterForm`,
     returns `200` with `UserDTO`. Note the contract says **200**, not 201; do not "improve" it.
   - Errors come from T-13 via T-19: 409 duplicate, 400 validation.
2. `AuthResource` at `@Path("/auth")`:
   - `POST /api/auth/login` — **open**, body `LoginForm`, returns `200` with `UserDTO` *"plus a
     session is created"*. On success call `CurrentUser.establish(request, sessionUser)` (T-18)
     so the session is rotated. 401 on bad credentials.
   - `POST /api/auth/logout` — `@Secured`, empty request body, returns **`204` with no body** and
     invalidates the session. Returning a body here would violate the contract.
3. Registration does **not** log the user in. The contract creates a session only at
   `/api/auth/login`, and T-27's UI flow depends on that.
4. Resource methods are thin: parse, delegate to `UserService`, map the result. No business logic,
   no validation, no `try/catch`.
5. `@Context HttpServletRequest` is injected for session work. `HttpSession` must not be touched
   anywhere outside these two resources and T-18.
6. Never log the request body of login or register — both contain a plaintext password.
7. After logout, the old `JSESSIONID` must not be reusable.

## Out of scope
- No password change, reset, or profile edit endpoints — not in specification §3.
- No user listing endpoint. Even for admins, enumerating users is not a stated requirement.
- No JSF beans (T-26).

## Acceptance criteria
1. `curl -X POST /api/users/register` with the contract's exact example body returns `200` and a
   `UserDTO` whose keys match the contract exactly.
2. Re-posting the same body returns `409`.
3. `POST /api/auth/login` with correct credentials returns `200`, a `UserDTO`, and a `Set-Cookie:
   JSESSIONID` header.
4. `POST /api/auth/login` with a wrong password returns `401` and **no** `Set-Cookie`.
5. `POST /api/auth/logout` with a valid session returns `204` and a genuinely empty body
   (`Content-Length: 0`).
6. `POST /api/auth/logout` with no session returns `401`.
7. Reusing the pre-logout `JSESSIONID` on `POST /api/pets` returns `401`.
8. The `JSESSIONID` after login differs from the one sent with the login request (T-18 rotation).
9. Server logs contain no plaintext password after exercising all of the above.

## Definition of Done
- [ ] All nine acceptance criteria demonstrated with `curl -i` output.
- [ ] Both resources are under 80 lines each — anything longer means logic leaked in from T-13.
- [ ] Each method's Javadoc quotes the contract line it implements.
