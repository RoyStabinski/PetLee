# T-13 · UserService — registration and authentication

| Field | Value |
|---|---|
| **Phase** | 4 — Business logic |
| **Depends on** | T-06, T-10, T-11, T-12 |
| **Blocks** | T-20 |
| **Estimate** | 4h |

## Goal
Implement the business rules behind `POST /api/users/register` and `POST /api/auth/login`
(specification §3, §9.3).

## Scope — files to create / modify
- `src/main/java/com/petlee/service/UserService.java`

## Requirements
1. `UserDTO register(RegisterForm form)`:
   - Validate before touching the database. Failures throw `ValidationException` (400) naming the
     offending field:
     `username` 3–20 chars, `[A-Za-z0-9_]` only; `password` minimum 8 characters;
     `fullName` non-blank, ≤ 50; `email` non-blank, ≤ 100, matches a basic address pattern;
     `phone` optional but if present ≤ 20 characters (matching `phone_number VARCHAR(20)` — the
     column was widened from 10 by **ADR-002 #9**, because the contract's own example sends the
     11-character `"050-1234567"`; a longer value would otherwise fail as a 500 at the database);
     `region` optional, ≤ 100.
   - Check `existsByUsername` and `existsByEmail`. Either hit throws `ConflictException` → **409**,
     as `api-contract.md` requires: *"409 if username/email already exists"*.
   - Hash the password with `PasswordHasher.hash` (T-10). **The plaintext password is never
     assigned to `User.password`.**
   - Force `role = USER`. A caller must not be able to self-elevate by posting `"role":"ADMIN"` —
     note `RegisterForm` has no `role` field precisely so this cannot happen, and the service must
     not add one.
   - Persist and return `UserMapper.toDto(saved)`.
2. `UserDTO authenticate(String username, String password)`:
   - Look up by username; if absent, or if `PasswordHasher.verify` fails, throw
     `UnauthorizedException` → **401**.
   - **The error message and code must be identical for "no such user" and "wrong password".**
     Distinguishing them tells an attacker which usernames exist.
   - On success return the `UserDTO`.
3. `Optional<UserDTO> findById(Long id)` — used by T-18 to rehydrate the session user.
4. Even though `existsByUsername` is checked first, the unique constraint from T-03 can still fire
   under a race between two simultaneous registrations. Catch the resulting
   `PersistenceException`/constraint violation and translate it to the same `ConflictException`.
   The pre-check is for a good message; the constraint is the actual guarantee.
5. Uniqueness comparison follows T-06: username case-sensitive, email case-insensitive.
6. The service returns **DTOs only**. A `User` entity must never escape this class — that is what
   keeps `password` from reaching the REST layer.
7. No JAX-RS or servlet imports. This class is transport-agnostic.

## Out of scope
- No session creation. `HttpSession` handling belongs to T-20 (the resource) and T-18 (the filter).
- No password *reset* or *change* flow — not in specification §3.
- No admin user management (T-34).

## Acceptance criteria
1. Registering with the contract's exact example body returns a `UserDTO` with `role` `"USER"` and
   no password field.
2. Registering the same username again throws `ConflictException`; the same for a duplicate email
   differing only in letter case.
3. A 7-character password throws `ValidationException` naming field `password`.
4. A 21-character phone throws `ValidationException`, not a database error; a 20-character one
   is accepted. (Was "11-character" before ADR-002 #9 widened the column to `VARCHAR(20)`.)
5. After registering, the stored `password_hash` in the database does not equal the plaintext and
   begins with `pbkdf2_sha256$`.
6. `authenticate` with correct credentials returns the `UserDTO`; with a wrong password and with an
   unknown username it throws `UnauthorizedException` with **byte-identical** message and code —
   assert equality of both exception messages in a test.
7. A `RegisterForm` carrying an unexpected `role` value in its JSON cannot produce an `ADMIN` user.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All seven acceptance criteria demonstrated.
- [ ] No `User` entity appears in any public method signature.
- [ ] Javadoc on every public method lists the exceptions thrown and their HTTP meaning.
