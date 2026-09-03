# T-10 · PasswordHasher implementation

| Field | Value |
|---|---|
| **Phase** | 2 — Security primitive |
| **Depends on** | T-01 |
| **Blocks** | T-03 (admin seed digest), T-13 |
| **Estimate** | 3h |

## Goal
Satisfy specification §4 "Data Encryption" — *"User passwords will not be stored as plain text in
the database"* — by replacing the empty `PasswordHasher` stub with a real, salted, iterated hash.

## Scope — files to create / modify
- `src/main/java/com/petlee/utilities/PasswordHasher.java` (currently an empty class body)
- `src/main/java/com/petlee/utilities/PasswordHasherCli.java` (tiny `main` that prints a digest,
  used to generate the admin seed value for T-03)

## Requirements
1. Algorithm: **PBKDF2WithHmacSHA256**, 210 000 iterations, 16-byte random salt from
   `SecureRandom`, 256-bit derived key. Chosen because it is in the JDK — no extra dependency, no
   licence question, and it is a genuinely accepted password KDF.
2. `static String hash(String plainPassword)` returns a **self-describing** encoded string:
   `pbkdf2_sha256$<iterations>$<base64Salt>$<base64Hash>`. Storing the parameters alongside the
   digest is what makes the iteration count upgradable later without invalidating existing users.
3. `static boolean verify(String plainPassword, String encoded)` parses the encoded string, derives
   with the *stored* salt and iteration count, and compares.
4. Comparison uses `java.security.MessageDigest.isEqual` — a constant-time comparison. `String.equals`
   short-circuits on the first differing byte and leaks timing information.
5. `verify` returns `false` for a malformed, empty or null `encoded` value. It must **never** throw
   on bad stored data — a corrupt row must fail the login, not crash the endpoint with a 500.
6. `hash(null)` and `hash("")` throw `IllegalArgumentException`. An empty password must not be
   silently hashable.
7. Two calls to `hash("samePassword")` produce **different** strings, because the salt is random.
   This is the property that defeats rainbow tables and must be tested explicitly.
8. The class is `final` with a private constructor — it is a stateless utility.
9. The output must fit `password_hash VARCHAR(255)` from T-03. Verify the encoded length
   (roughly 100 characters) rather than assuming.
10. `PasswordHasherCli` prints `hash(args[0])` so T-03's seeded admin row gets a genuine digest.

## Out of scope
- No user lookup, no session handling, no login flow (T-13, T-18).
- Do not add BCrypt/Argon2 libraries; the JDK primitive is sufficient and dependency-free.
- No password *strength* rules — that validation belongs to T-13.

## Acceptance criteria
1. `verify("secret123", hash("secret123"))` is `true`.
2. `verify("wrong", hash("secret123"))` is `false`.
3. `hash("x")` called twice returns two different strings, and `verify("x", …)` is `true` for both.
4. `verify("x", null)`, `verify("x", "")` and `verify("x", "garbage")` all return `false` and throw
   nothing.
5. `hash(null)` and `hash("")` throw `IllegalArgumentException`.
6. The encoded output is ≤ 255 characters and starts with `pbkdf2_sha256$`.
7. `grep -ri "password" --include=*.log` over a run of the login flow shows no plaintext password.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All seven acceptance criteria covered by JUnit tests written **in this task** (do not defer
      them to T-37 — this is security-critical code and ships with its own tests).
- [ ] The empty stub body is gone; no `TODO` remains.
- [ ] `PasswordHasherCli` output pasted into T-03's `seed.sql` for the admin user.
