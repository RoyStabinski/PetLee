# T-36 · Test infrastructure

| Field | Value |
|---|---|
| **Phase** | 9 — Verification |
| **Depends on** | T-01, T-02, T-03 |
| **Blocks** | T-37, T-38, T-39 |
| **Estimate** | 5h |

## Goal
Make tests runnable, isolated and repeatable using only JUnit 5 and the platform, so the three
test tasks that follow can focus on assertions instead of setup.

## Scope — files to create / modify
- `src/test/resources/META-INF/persistence.xml` (persistence unit `petlee-test-pu`)
- `src/test/java/com/petlee/test/DatabaseTest.java` (base class)
- `src/test/java/com/petlee/test/TestData.java` (entity builders)
- `src/test/java/com/petlee/test/Fakes.java` (hand-written repository doubles)
- `src/test/README.md`

## Requirements
1. Two test tiers, separated by filename so they run independently:
   - `*Test.java` — unit and repository tests, run by surefire in `mvn test`.
   - `*IT.java` — REST integration tests against a deployed server, run by failsafe in `mvn verify`.
2. **Tests run against a real PostgreSQL database**, not an in-memory substitute. Specification §8
   names PostgreSQL, and ADR-003 forbids adding H2. This is also the better test: the suite now
   exercises the real dialect, `ON DELETE RESTRICT`, and the partial unique index from T-03 — none
   of which an in-memory database models faithfully.
3. Isolation comes from a **separate database**, `petlee_test`, created by the same `schema.sql`
   from T-03. Connection details come from system properties
   (`-Dpetlee.test.db.url`, `.user`, `.password`) with localhost defaults, so no developer's
   settings are baked into the repository.
4. `petlee-test-pu` is `transaction-type="RESOURCE_LOCAL"` with an application-managed
   `EntityManagerFactory` created by `DatabaseTest`. This is the one place `RESOURCE_LOCAL` and
   `Persistence.createEntityManagerFactory` are permitted: tests run outside a container, so there
   is no JTA to inherit. Production remains JTA (T-02).
5. `DatabaseTest` provides per-test `EntityManager` setup/teardown and a `clean()` that truncates
   all four tables in foreign-key-safe order. Every test starts from a known state; no test may
   depend on another's leftovers or on execution order.
6. `TestData` offers fluent builders — `aUser()`, `anAdmin()`, `aCategory()`, `aPet()`,
   `aPetImage()` — with sensible defaults and overridable fields. Hand-built entity graphs in every
   test are where test suites go to die.
7. `Fakes` holds **hand-written test doubles** for each repository: small classes backed by a
   `HashMap`, implementing the same methods and recording calls where a test needs to assert them.
   ADR-003 forbids a mocking library, and for five repositories with narrow interfaces a fake is
   about as much code as the mock setup would be — and reads better in the test.
8. To make requirement 7 possible, service classes must accept their repositories through a
   **constructor**, alongside the no-arg constructor CDI requires. Field injection with no
   constructor cannot be unit-tested without a container. Record this as a constraint that T-13,
   T-14, T-15 and T-16 must satisfy.
9. `mvn test` must not require a running application server. `mvn verify` may.
10. `src/test/README.md` states how to create `petlee_test`, how to run each tier, and which system
    properties each accepts.

## Out of scope
- No test cases (T-37, T-38, T-39).
- No CI pipeline configuration.
- No in-memory database, no mocking library, no assertion library beyond JUnit's (ADR-003).

## Acceptance criteria
1. `mvn test` runs green from a clean checkout against a freshly created `petlee_test` database.
2. `DatabaseTest` subclasses see an empty database at the start of every test method.
3. Two tests inserting the same username both pass, in either order.
4. `TestData.aPet().build()` produces a persistable `Pet` with a valid owner and category.
5. A fake repository from `Fakes` can be injected into a service through its constructor and the
   service behaves correctly with no database at all.
6. `mvn verify` runs failsafe separately from surefire.
7. A deliberately failing assertion fails the build.
8. `grep -rE "mockito|h2database|rest-assured|assertj" pom.xml src/test/` returns nothing.

## Definition of Done
- [ ] All eight acceptance criteria demonstrated.
- [ ] `src/test/resources` contains no real credentials.
- [ ] `src/test/README.md` gives the exact `createdb` and `psql` commands.
- [ ] The constructor-injection constraint in requirement 8 is communicated to T-13…T-16.
