# T-06 · UserRepository

| Field | Value |
|---|---|
| **Phase** | 1 — Data access |
| **Depends on** | T-05 |
| **Blocks** | T-13 |
| **Estimate** | 2h |

## Goal
Provide persistence operations for users: lookup by credentials-relevant fields, uniqueness
checks, and registration persistence (specification §9.2).

## Scope — files to create / modify
- `src/main/java/com/petlee/repository/UserRepository.java`

## Requirements
1. `UserRepository extends AbstractRepository<User, Long>`.
2. `Optional<User> findByUsername(String username)` — JPQL, parameter-bound. Returns
   `Optional.empty()` rather than throwing `NoResultException`; callers should not handle
   exceptions for an ordinary "not found".
3. `Optional<User> findByEmail(String email)` — same contract.
4. `boolean existsByUsername(String username)` and `boolean existsByEmail(String email)` —
   implemented as `SELECT COUNT(u)`, not by loading the entity. T-13 calls these to produce the
   contract's 409 response, so they run on every registration.
5. `Optional<User> findById(Long id)` inherited from the base class; do not re-implement.
6. **Every query uses named parameters** (`:username`). String concatenation into JPQL is a
   review blocker — this is the layer where SQL injection would enter the system.
7. Username comparison is case-sensitive, email comparison is case-insensitive
   (`LOWER(u.email) = LOWER(:email)`). Document this asymmetry in Javadoc so T-13 and T-37 agree
   on it; emails are case-insensitive by RFC, usernames are the user's exact chosen identity.
8. No password hashing here. The repository stores whatever string it is given; hashing is T-10,
   invoked by T-13.

## Out of scope
- No validation of email format or password strength (T-13).
- No `UserDTO` conversion (T-11).
- No authentication logic — the repository never compares passwords.

## Acceptance criteria
1. `findByUsername` on a missing username returns `Optional.empty()` and throws nothing.
2. `existsByEmail("A@B.com")` returns `true` when the stored email is `a@b.com`.
3. `existsByUsername("Admin")` returns `false` when the stored username is `admin`.
4. `save` on a new `User` populates `userId` and `createdAt` (the `@PrePersist` hook from T-04).
5. Saving a second user with an existing username fails with a constraint violation originating
   from the database, not only from a Java check.
6. `grep -n '"\s*+\s*' UserRepository.java` finds no string-concatenated query fragments.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All six acceptance criteria demonstrated.
- [ ] Every method has Javadoc stating its null/empty behaviour.
- [ ] No method returns `null`; absence is always `Optional.empty()`.
