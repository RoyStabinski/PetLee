# T-37 · Unit tests for utilities, mappers, and business rules

| Field | Value |
|---|---|
| **Phase** | 9 — Verification |
| **Depends on** | T-36 |
| **Blocks** | none |
| **Estimate** | 8h |

## Goal
Prove every business rule in specification §5 and every privacy rule in §6 with fast tests that
need no database and no server.

## Scope — files to create / modify
- `src/test/java/com/petlee/utilities/PasswordHasherTest.java` (extends the tests shipped with T-10)
- `src/test/java/com/petlee/mapper/PetMapperTest.java`
- `src/test/java/com/petlee/mapper/UserMapperTest.java`
- `src/test/java/com/petlee/service/UserServiceTest.java`
- `src/test/java/com/petlee/service/PetServiceTest.java`

## Requirements
1. Services are tested with the **hand-written repository fakes from `Fakes` (T-36)**, injected
   through each service's constructor. These are unit tests: no database, no HTTP, no mocking
   library (ADR-003), and they run in milliseconds.
2. `PetMapperTest` must cover the contact-masking flag exhaustively, because it is the mechanism
   behind specification §6:
   - `toDetailDto(pet, false)` → all three owner fields null.
   - `toDetailDto(pet, true)` → all three populated.
   - No other field differs between the two calls.
3. `UserMapperTest` asserts by reflection that `UserDTO` has **no** field whose name contains
   "password", and that a serialised `UserDTO` has exactly the six contract keys. A field added
   carelessly later must fail this test.
4. `UserServiceTest` covers, one test each:
   - Successful registration returns a `UserDTO` with `role = USER`.
   - Duplicate username → `ConflictException`; duplicate email (differing in case) → `ConflictException`.
   - Each validation rule from T-13 requirement 1 → `ValidationException` naming the field.
   - The stored password is not the plaintext, and `PasswordHasher.verify` accepts it.
   - `authenticate` with an unknown user and with a wrong password throw exceptions whose messages
     and codes are **equal** — the user-enumeration defence.
   - Registration cannot produce an `ADMIN`.
5. `PetServiceTest` covers, one test each:
   - Guest detail view masks contact fields; authenticated view does not.
   - `create` with an unknown category → `NotFoundException`.
   - `create` always sets the owner from the passed caller id.
   - `update` by a non-owner → `ForbiddenException`; **by an admin who is not the owner → also
     `ForbiddenException`** (T-15 requirement 4).
   - `delete` by owner → allowed; by admin → allowed; by a stranger → `ForbiddenException`.
   - `OptimisticLockException` from the repository becomes `ConflictException` with code
     `STALE_PET`.
   - `findGallery` requests only `AVAILABLE` pets from the repository.
6. Test names read as sentences: `register_whenUsernameTaken_throwsConflict`. A failing test should
   name the broken rule without anyone opening the file.
7. One behaviour per test. No test asserts two unrelated rules.
8. Every test in requirements 2, 4 and 5 carries a comment citing the specification section or
   contract line it defends.

## Out of scope
- No database access (T-38).
- No HTTP (T-39).
- No UI testing.

## Acceptance criteria
1. `mvn test` runs the whole suite in under 30 seconds with no database.
2. Every rule listed in requirements 2, 4 and 5 has a dedicated, named test.
3. Deliberately inverting the `includeContact` flag in `PetMapper` makes exactly the masking tests
   fail — proving they actually test the rule.
4. Deliberately allowing admins to edit others' pets in `PetService` makes exactly one test fail.
5. Removing the duplicate-email check makes exactly one test fail.
6. Line coverage of `UserService`, `PetService`, `PetMapper` and `PasswordHasher` is at least 85%,
   reported by a coverage run.
7. The suite passes ten consecutive runs — no order dependence, no flakiness.

## Definition of Done
- [ ] All seven acceptance criteria demonstrated; 3, 4 and 5 are mutation checks and must actually
      be performed, then reverted.
- [ ] Coverage figures pasted into the PR.
- [ ] No test touches a real database or network.
