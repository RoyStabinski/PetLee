# T-38 · Repository and persistence integration tests

| Field | Value |
|---|---|
| **Phase** | 9 — Verification |
| **Depends on** | T-36 |
| **Blocks** | none |
| **Estimate** | 6h |

## Goal
Prove the data layer does what the services assume — correct filtering, real optimistic locking,
real cascade deletes — against an actual database.

## Scope — files to create / modify
- `src/test/java/com/petlee/repository/UserRepositoryTest.java`
- `src/test/java/com/petlee/repository/CategoryRepositoryTest.java`
- `src/test/java/com/petlee/repository/PetRepositoryTest.java`
- `src/test/java/com/petlee/repository/PetImageRepositoryTest.java`
- `src/test/java/com/petlee/persistence/ConcurrencyTest.java`

## Requirements
1. All extend `DatabaseTest` (T-36) and run against the real `petlee_test` PostgreSQL database.
   There is no in-memory tier and no `@Tag` split — ADR-003 removed H2, which means every test now
   exercises the real dialect, and the constraints below need no special handling.
2. `UserRepositoryTest`: `findByUsername` present/absent; email lookup case-insensitive; username
   lookup case-sensitive; `existsBy*` correctness; `save` populates `userId` and `createdAt`;
   duplicate username rejected by the database constraint.
3. `PetRepositoryTest` — the largest suite, since T-08 carries the most logic:
   - `findByFilter` with no criteria returns only `AVAILABLE` pets, newest first. Seed pets with
     explicitly different `createdAt` values; relying on insertion order proves nothing.
   - Each filter alone, and all three combined, return the correct subset.
   - `findDetailById` returns a graph whose `category`, `owner` and `images` are all readable
     **after the `EntityManager` is closed**. This is the `LazyInitializationException` regression
     test and is mandatory.
   - `findByOwnerId` includes `REMOVED` pets and excludes other owners' pets.
   - `findAllForAdmin` returns every status.
   - `findDetailById` on an unknown id returns `Optional.empty()`.
4. **N+1 regression test**: seed 20 pets each with 2 images, run `findByFilter`, and assert the
   statement count is bounded (≤ 3) using a Hibernate `StatisticsImplementor` or a counting
   `StatementInspector`. Without this, a later refactor reintroduces N+1 silently and only
   production notices.
5. `PetImageRepositoryTest`: ordering with main first; `findMainByPetId` empty case;
   `clearMainFlag` then set-main succeeds; `countByPetId`; deleting the pet removes the images.
6. Cover the two constraints that only a real database enforces: the partial unique index on
   `is_main` (T-03 requirement 7) and `ON DELETE RESTRICT` on `category_id`. Both are now ordinary
   tests rather than a tagged subset.
7. `ConcurrencyTest` is the proof of specification §4's concurrency control:
   - Load one `Pet` in two separate `EntityManager`s.
   - Commit an update through the first.
   - Commit a conflicting update through the second and assert `OptimisticLockException`.
   - Assert the first writer's data survived — the point of the requirement is that nothing is
     silently overwritten.
8. A connection-leak test: run 200 transactional service calls, some throwing, and assert the
   server-managed pool still hands out connections afterwards. This verifies that no code bypasses
   container-managed transactions (T-05 requirement 2).
9. Every test cleans up after itself via `DatabaseTest`; none depends on execution order.

## Out of scope
- No HTTP (T-39).
- No service-layer rules (T-37 covers those with mocks).

## Acceptance criteria
1. `mvn test` runs the whole suite green against the `petlee_test` PostgreSQL database.
2. Every test in the suite runs against PostgreSQL — a search for `@Tag` and for H2 returns nothing.
3. The ordering test fails if `ORDER BY created_at DESC` is removed from `PetRepository`.
4. The lazy-loading test fails if the fetch joins are removed from `findDetailById`.
5. The N+1 test fails if the gallery fetch join is removed, with the statement count in the failure
   message.
6. `ConcurrencyTest` fails if `@Version` is removed from `Pet` — perform this mutation and revert it.
7. The `is_main` uniqueness test fails if the partial index is dropped from `schema.sql`.
8. The leak test fails if a service is changed to create its own `EntityManager` instead of using
   the injected persistence context.
9. The whole suite passes ten consecutive runs in random order.

## Definition of Done
- [ ] All nine acceptance criteria demonstrated; 3, 4, 5, 6 and 8 are mutation checks that must
      actually be run and then reverted.
- [ ] `src/test/README.md` states how to create and reset the `petlee_test` database.
- [ ] No test depends on another test's data.
